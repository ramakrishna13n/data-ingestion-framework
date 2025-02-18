import * as cdk from 'aws-cdk-lib';
import * as appsync from 'aws-cdk-lib/aws-appsync';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as path from "node:path";
import {IDomain} from "aws-cdk-lib/aws-opensearchservice";

interface AppSyncStackProps extends cdk.StackProps {
    readonly redshiftLambda: lambda.Function;
    readonly openSearchEndpoint: IDomain;
}

export class AppSyncStack extends cdk.Stack {
    public readonly api: appsync.GraphqlApi;

    constructor(scope: cdk.App, id: string, props: AppSyncStackProps) {
        super(scope, id, props);
        this.api = this.createGraphQLApi();
        const openSearchEndpoint = props.openSearchEndpoint;
        const openSearchDataSource = this.createOpenSearchDataSource(openSearchEndpoint);
        const redshiftDataSource = this.createRedshiftDataSource(props.redshiftLambda);
        this.configureOpenSearchResolvers(openSearchDataSource);
        this.configureRedshiftResolver(redshiftDataSource);
    }

    private createGraphQLApi(): appsync.GraphqlApi {
        return new appsync.GraphqlApi(this, 'StockGraphQLAPI', {
            name: 'StockGraphQLAPI',
            definition: appsync.Definition.fromFile(path.join(__dirname, '../graphql/schema.graphql')),
            authorizationConfig: {
                defaultAuthorization: {
                    authorizationType: appsync.AuthorizationType.IAM,
                },
            },
        });
    }

    private createOpenSearchDataSource(openSearchEndpoint: IDomain): appsync.OpenSearchDataSource {
        return this.api.addOpenSearchDataSource(
            'OpenSearchDataSource',
            openSearchEndpoint
        );
    }

    private createRedshiftDataSource(lambdaFn: lambda.Function): appsync.LambdaDataSource {
        return this.api.addLambdaDataSource(
            'RedshiftDataSource',
            lambdaFn
        );
    }

    private configureOpenSearchResolvers(dataSource: appsync.HttpDataSource): void {
        const openSearchQueries = [
            'search',
            'filter',
            'sort',
            'paginate',
            'aggregate'
        ];

        openSearchQueries.forEach(query => {
            dataSource.createResolver(`opensearch-${query}`, {
                typeName: 'Query',
                fieldName: query,
                requestMappingTemplate: appsync.MappingTemplate.fromFile(
                    `graphql/resolvers/query/${query}.vtl`
                ),
                responseMappingTemplate: appsync.MappingTemplate.fromString(
                    '$util.toJson($context.result)'
                ),
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