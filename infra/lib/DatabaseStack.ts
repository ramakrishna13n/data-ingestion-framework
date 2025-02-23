import * as cdk from 'aws-cdk-lib';
import * as redshift from 'aws-cdk-lib/aws-redshiftserverless';
import * as opensearch from 'aws-cdk-lib/aws-opensearchservice';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import {Construct} from 'constructs';

interface DatabaseStackProps extends cdk.StackProps {
    vpc: ec2.Vpc;
    dataBucketArn: string;
    openSearchVpcEndpointId: string;
    openSearchSecurityGroup: ec2.SecurityGroup;
}

export class DatabaseStack extends cdk.Stack {
    public readonly redshiftJdbcUrl: string;
    public readonly redshiftIAMRole: iam.Role;
    public readonly openSearchIAMRole: iam.Role;
    public readonly openSearchEndpoint: opensearch.IDomain;

    constructor(scope: Construct, id: string, props: DatabaseStackProps) {
        super(scope, id, props);

        this.redshiftIAMRole = new iam.Role(this, 'RedshiftRole', {
            assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
            description: 'IAM role for Lambda to access Redshift',
            managedPolicies: [
                iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AWSLambdaVPCAccessExecutionRole'),
                iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonRedshiftDataFullAccess'),
            ],
        });

        this.openSearchIAMRole = new iam.Role(this, 'OpenSearchIAMRole', {
            assumedBy: new iam.ServicePrincipal('appsync.amazonaws.com'),
            description: 'IAM role for AppSync & Batch to access OpenSearch',
        });

        this.openSearchIAMRole.addToPolicy(
            new iam.PolicyStatement({
                effect: iam.Effect.ALLOW,
                actions: ['es:ESHttpGet', 'es:ESHttpPost'],
                resources: ['arn:aws:es:us-west-2:123456789012:domain/my-opensearch-domain/*'],
            })
        );

        const redshiftNamespace = new redshift.CfnNamespace(this, 'RedshiftNamespace', {
            namespaceName: 'redshift-stock',
            dbName: 'dev',
            adminUsername: 'admin',
            adminUserPassword: 'Admin123!',
            iamRoles: [this.redshiftIAMRole.roleArn],
        });

        const redshiftWorkgroup = new redshift.CfnWorkgroup(this, 'RedshiftWorkgroup', {
            namespaceName: redshiftNamespace.namespaceName,
            workgroupName: 'redshift-wg',
            publiclyAccessible: false,
            baseCapacity: 8,
            configParameters: [
                {parameterKey: 'auto_mv', parameterValue: 'false'},
                {parameterKey: 'enable_user_activity_logging', parameterValue: 'false'},
            ],
        });
        redshiftWorkgroup.addDependency(redshiftNamespace);

        this.redshiftJdbcUrl = `jdbc:redshift-serverless://${redshiftWorkgroup.workgroupName}.${this.region}.redshift-serverless.amazonaws.com:5439/dev`;

        const selectedSubnet = props.vpc.selectSubnets({
            subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
        }).subnets[0];

        this.openSearchEndpoint = new opensearch.Domain(this, 'OpenSearchCluster', {
            version: opensearch.EngineVersion.OPENSEARCH_2_17,
            vpc: props.vpc,
            vpcSubnets: [{subnets: [selectedSubnet]}],
            capacity: {
                dataNodes: 1,
                dataNodeInstanceType: 't3.small.search',
                multiAzWithStandbyEnabled: false,
            },
            automatedSnapshotStartHour: 0,
            ebs: {
                volumeSize: 10,
                volumeType: ec2.EbsDeviceVolumeType.GP3,
            },
            nodeToNodeEncryption: false,
            encryptionAtRest: {enabled: false},
            accessPolicies: [
                new iam.PolicyStatement({
                    effect: iam.Effect.ALLOW,
                    principals: [this.openSearchIAMRole],
                    actions: ['es:ESHttp*'],
                    resources: ['*'],
                }),
            ],
        });

        new ec2.CfnRoute(this, 'OpenSearchVpcEndpointRoute', {
            routeTableId: props.vpc.privateSubnets[0].routeTable.routeTableId,
            destinationCidrBlock: '0.0.0.0/0',
            vpcEndpointId: props.openSearchVpcEndpointId,
        });

        new cdk.CfnOutput(this, 'OpenSearchVpcEndpointOutput', {
            value: props.openSearchVpcEndpointId,
            description: 'VPC Endpoint ID for OpenSearch',
        });
    }
}
