import * as cdk from 'aws-cdk-lib';
import * as batch from 'aws-cdk-lib/aws-batch';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as logs from 'aws-cdk-lib/aws-logs';
import {Construct} from 'constructs';

interface BatchStackProps extends cdk.StackProps {
    vpc: ec2.Vpc;
    redshiftIAMRole: iam.Role;
    openSearchIAMRole: iam.Role;
}

export class BatchStack extends cdk.Stack {
    constructor(scope: Construct, id: string, props: BatchStackProps) {
        super(scope, id, props);

        const {vpc, redshiftIAMRole, openSearchIAMRole} = props;

        const batchExecutionRole = new iam.Role(this, 'BatchExecutionRole', {
            assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
            managedPolicies: [
                iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonECSTaskExecutionRolePolicy'),
            ]
        });

        batchExecutionRole.addToPolicy(new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: ['es:ESHttpPut', 'es:ESHttpPost', 'es:ESHttpGet'],
            resources: ['arn:aws:es:us-west-2:123456789012:domain/my-opensearch-domain/*']
        }));

        batchExecutionRole.addToPolicy(new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: [
                'redshift:GetClusterCredentials',
                'redshift:DescribeClusters',
                'redshift:ExecuteQuery'
            ],
            resources: ['arn:aws:redshift:us-west-2:123456789012:cluster/my-redshift-cluster']
        }));

        const computeEnv = new batch.CfnComputeEnvironment(this, 'BatchComputeEnv', {
            type: 'MANAGED',
            computeResources: {
                type: 'FARGATE',
                subnets: vpc.selectSubnets({subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS}).subnetIds,
                securityGroupIds: [batchExecutionRole.roleArn],
                maxvCpus: 4, // Reduced max vCPUs to control costs
                desiredvCpus: 0,
                instanceTypes: ['FARGATE_SPOT']
            },
            state: 'ENABLED',
            serviceRole: batchExecutionRole.roleArn
        });

        const jobQueue = new batch.CfnJobQueue(this, 'BatchJobQueue', {
            computeEnvironmentOrder: [{order: 1, computeEnvironment: computeEnv.ref}],
            priority: 1,
            state: 'ENABLED'
        });

        const logGroup = new logs.LogGroup(this, 'BatchLogGroup', {
            logGroupName: '/aws/batch/spring-batch-app',
            removalPolicy: cdk.RemovalPolicy.DESTROY,
            retention: logs.RetentionDays.ONE_WEEK // Set log retention to reduce storage costs
        });

        const repository = ecr.Repository.fromRepositoryName(this, 'SpringBatchECR', 'spring-batch-app');

        new batch.CfnJobDefinition(this, 'SpringBatchJobDefinition', {
            type: 'container',
            platformCapabilities: ['FARGATE'],
            containerProperties: {
                image: repository.repositoryUri,
                executionRoleArn: batchExecutionRole.roleArn,
                networkConfiguration: {
                    assignPublicIp: 'DISABLED'
                },
                logConfiguration: {
                    logDriver: 'awslogs',
                    options: {
                        'awslogs-group': logGroup.logGroupName,
                        'awslogs-region': this.region,
                        'awslogs-stream-prefix': 'batch-job'
                    }
                },
                memory: 2048,
                vcpus: 1,
                command: ['java', '-Xmx1536m', '-jar', '/app/spring-batch.jar'],
                environment: [
                    {name: 'SPRING_PROFILES_ACTIVE', value: 'prod'},
                    {name: 'REDSHIFT_JDBC_URL', value: `jdbc:redshift-serverless://${this.region}/dev`},
                    {name: 'OPENSEARCH_ENDPOINT', value: `https://my-opensearch-endpoint`}
                ]
            },
            retryStrategy: {
                attempts: 2
            }
        });
    }
}