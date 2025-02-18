import * as cdk from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';

interface LambdaStackProps extends cdk.StackProps {
    vpc: ec2.Vpc;
    redshiftJdbcUrl: string;
    redshiftIAMRole: string;
}

export class LambdaStack extends cdk.Stack {
    public readonly resolverLambda: lambda.Function;

    constructor(scope: cdk.App, id: string, props: LambdaStackProps) {
        super(scope, id, props);

        const lambdaExecutionRole = new iam.Role(this, 'LambdaExecutionRole', {
            assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
            managedPolicies: [
                iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"),
                iam.ManagedPolicy.fromAwsManagedPolicyName("AmazonRedshiftFullAccess"),
                iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaVPCAccessExecutionRole")
            ],
            inlinePolicies: {
                'RedshiftIAMRoleAccess': new iam.PolicyDocument({
                    statements: [
                        new iam.PolicyStatement({
                            effect: iam.Effect.ALLOW,
                            actions: ['sts:AssumeRole'],
                            resources: [props.redshiftIAMRole]
                        })
                    ]
                })
            }
        });

        this.resolverLambda = new lambda.Function(this, 'GraphQLResolverLambda', {
            runtime: lambda.Runtime.JAVA_17,
            handler: 'com.search.SearchLambdaHandler::handleRequest',
            code: lambda.Code.fromAsset('../search/target/search-1.0-SNAPSHOT.jar'),
            memorySize: 1024,
            timeout: cdk.Duration.seconds(15),
            vpc: props.vpc,
            environment: {
                REDSHIFT_JDBC_URL: props.redshiftJdbcUrl,
                REDSHIFT_IAM_ROLE: props.redshiftIAMRole,
            },
            role: lambdaExecutionRole,
            snapStart: lambda.SnapStartConf.ON_PUBLISHED_VERSIONS
        });
    }
}