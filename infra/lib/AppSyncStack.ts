import * as cdk from 'aws-cdk-lib';
import * as appsync from 'aws-cdk-lib/aws-appsync';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import {Construct} from 'constructs';

interface AppSyncStackProps extends cdk.StackProps {
    readonly redshiftLambda: lambda.Function;
    readonly openSearchVpcEndpointId: string;
    readonly vpc: ec2.Vpc;
    readonly openSearchSecurityGroup: ec2.SecurityGroup;
}

export class AppSyncStack extends cdk.Stack {
    public readonly api: appsync.GraphqlApi;

    constructor(scope: Construct, id: string, props: AppSyncStackProps) {
        super(scope, id, props);
        const {redshiftLambda, openSearchVpcEndpointId, vpc, openSearchSecurityGroup} = props;

        this.api = this.createGraphQLApi();

        const appsyncOpenSearchRole = new iam.Role(this, 'AppSyncOpenSearchRole', {
            assumedBy: new iam.ServicePrincipal('appsync.amazonaws.com'),
        });

        appsyncOpenSearchRole.addToPolicy(new iam.PolicyStatement({
            actions: ['es:ESHttpGet', 'es:ESHttpPost'],
            resources: [`arn:aws:es:${this.region}:${this.account}:domain/my-opensearch-domain/*`],
        }));

        new ec2.CfnRoute(this, 'AppSyncOpenSearchVpcRoute', {
            routeTableId: vpc.privateSubnets[0].routeTable.routeTableId,
            destinationCidrBlock: '0.0.0.0/0',
            vpcEndpointId: openSearchVpcEndpointId,
        });

        openSearchSecurityGroup.addIngressRule(
            ec2.Peer.ipv4(vpc.vpcCidrBlock),
            ec2.Port.tcp(443),
            'Allow AppSync to communicate with OpenSearch via VPC Endpoint'
        );

        const openSearchDataSource = this.api.addHttpDataSource(
            'OpenSearchDataSource',
            `https://${openSearchVpcEndpointId}`,
            {
                authorizationConfig: {
                    signingRegion: this.region,
                    signingServiceName: 'es',
                },
            }
        );

        openSearchDataSource.grantPrincipal.addToPrincipalPolicy(new iam.PolicyStatement({
            actions: ['es:ESHttpGet', 'es:ESHttpPost'],
            resources: [`arn:aws:es:${this.region}:${this.account}:domain/my-opensearch-domain/*`],
        }));

        const redshiftDataSource = this.createRedshiftDataSource(redshiftLambda);

        this.configureOpenSearchResolvers(openSearchDataSource);
        this.configureRedshiftResolver(redshiftDataSource);
    }

    private createGraphQLApi(): appsync.GraphqlApi {
        return new appsync.GraphqlApi(this, 'StockGraphQLAPI', {
            name: 'StockGraphQLAPI',
            definition: appsync.Definition.fromFile('graphql/schema.graphql'),
            authorizationConfig: {
                defaultAuthorization: {
                    authorizationType: appsync.AuthorizationType.IAM,
                },
            },
        });
    }

    private createRedshiftDataSource(lambdaFn: lambda.Function): appsync.LambdaDataSource {
        return this.api.addLambdaDataSource(
            'RedshiftDataSource',
            lambdaFn
        );
    }

    private configureOpenSearchResolvers(dataSource: appsync.HttpDataSource): void {
        const openSearchQueries = ['search', 'filter', 'sort', 'paginate', 'aggregate'];

        openSearchQueries.forEach(query => {
            dataSource.createResolver(`opensearch-${query}`, {
                typeName: 'Query',
                fieldName: query,
                requestMappingTemplate: appsync.MappingTemplate.fromFile(`graphql/resolvers/query/${query}.vtl`),
                responseMappingTemplate: appsync.MappingTemplate.fromString('$util.toJson($context.result)'),
            });
        });
    }

    private configureRedshiftResolver(dataSource: appsync.LambdaDataSource): void {
        dataSource.createResolver("redshift", {
            typeName: 'Query',
            fieldName: 'runRedshiftQuery',
            requestMappingTemplate: appsync.MappingTemplate.lambdaRequest(),
            responseMappingTemplate: appsync.MappingTemplate.lambdaResult(),
            maxBatchSize: 100
        });
    }
}
