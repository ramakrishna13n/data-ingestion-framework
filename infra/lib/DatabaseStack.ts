import * as cdk from 'aws-cdk-lib';
import * as redshift from 'aws-cdk-lib/aws-redshiftserverless';
import * as opensearch from 'aws-cdk-lib/aws-opensearchservice';
import {IDomain} from 'aws-cdk-lib/aws-opensearchservice';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';

interface DatabaseStackProps extends cdk.StackProps {
    vpc: ec2.Vpc;
    dataBucketArn: string;
}

export class DatabaseStack extends cdk.Stack {
    public readonly redshiftJdbcUrl: string;
    public readonly redshiftIAMRole: iam.Role;
    public readonly openSearchEndpoint: IDomain;

    constructor(scope: cdk.App, id: string, props: DatabaseStackProps) {
        super(scope, id, props);

        this.redshiftIAMRole = new iam.Role(this, 'RedshiftRole', {
            assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
            description: 'IAM role for Lambda to access Redshift',
            managedPolicies: [
                iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AWSLambdaVPCAccessExecutionRole'),
                iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonRedshiftDataFullAccess')
            ]
        });

        const redshiftS3Role = new iam.Role(this, 'RedshiftS3Role', {
            assumedBy: new iam.ServicePrincipal('redshift-serverless.amazonaws.com'),
            description: 'IAM role for Redshift to access S3',
        });

        redshiftS3Role.addToPolicy(new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: [
                "s3:GetObject",
                "s3:ListBucket",
                "s3:GetBucketLocation"
            ],
            resources: [
                props.dataBucketArn,
                `${props.dataBucketArn}/*`,
            ]
        }));

        const namespace = new redshift.CfnNamespace(this, 'RedshiftNamespace', {
            namespaceName: 'redshift-stock',
            dbName: 'dev',
            adminUsername: 'admin',
            adminUserPassword: 'Admin123!', // Can use secrets manager or parameter store
            iamRoles: [redshiftS3Role.roleArn]
        });

        const redshiftWorkgroup = new redshift.CfnWorkgroup(this, 'RedshiftWorkgroup', {
            namespaceName: namespace.namespaceName,
            workgroupName: 'redshift-wg',
            publiclyAccessible: false,
            baseCapacity: 8, // Minimum capacity units
            configParameters: [{
                parameterKey: 'auto_mv',
                parameterValue: 'false'
            },
                {
                    parameterKey: 'enable_user_activity_logging',
                    parameterValue: 'false'
                }]
        });
        redshiftWorkgroup.addDependency(namespace);

        this.redshiftJdbcUrl = `jdbc:redshift-serverless://${redshiftWorkgroup.workgroupName}.${this.region}.redshift-serverless.amazonaws.com:5439/dev`;

        const selectedSubnet = props.vpc.selectSubnets({
            subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS
        }).subnets[0];
        const openSearchDomain = new opensearch.Domain(this, 'OpenSearchCluster', {
            version: opensearch.EngineVersion.OPENSEARCH_2_17,
            vpc: props.vpc,
            vpcSubnets: [{subnets: [selectedSubnet]}],
            capacity: {
                dataNodes: 1,
                dataNodeInstanceType: 't3.small.search',
                multiAzWithStandbyEnabled: false
            },
            automatedSnapshotStartHour: 0,
            ebs: {
                volumeSize: 10,
                volumeType: ec2.EbsDeviceVolumeType.GP3
            },
            nodeToNodeEncryption: false,
            encryptionAtRest: {
                enabled: false
            },
            accessPolicies: [
                new iam.PolicyStatement({
                    effect: iam.Effect.ALLOW,
                    principals: [new iam.AnyPrincipal()],
                    actions: ['es:ESHttp*'],
                    resources: ['*']
                })
            ]
        });

        this.openSearchEndpoint = openSearchDomain;
    }
}