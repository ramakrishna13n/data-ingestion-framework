import * as cdk from 'aws-cdk-lib';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';

interface StorageStackProps extends cdk.StackProps {
    bucketName?: string;
    tableName?: string;
}

export class StorageStack extends cdk.Stack {
    public readonly s3Bucket: s3.Bucket;
    public readonly bucketArn: string;
    public readonly dynamoTable: dynamodb.Table;

    constructor(scope: cdk.App, id: string, props?: StorageStackProps) {
        super(scope, id, props);

        this.s3Bucket = new s3.Bucket(this, 'StockMarketDataBucket', {
            bucketName: props?.bucketName,
            removalPolicy: cdk.RemovalPolicy.DESTROY,
            autoDeleteObjects: true,
            encryption: s3.BucketEncryption.S3_MANAGED,
            versioned: true,
            blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
            enforceSSL: true,
        });
        this.bucketArn = this.s3Bucket.bucketArn;

        this.dynamoTable = new dynamodb.Table(this, 'CacheTable', {
            tableName: props?.tableName,
            partitionKey: {name: 'queryKey', type: dynamodb.AttributeType.STRING},
            billingMode: dynamodb.BillingMode.PROVISIONED,
            readCapacity: 1,
            writeCapacity: 1,
            encryption: dynamodb.TableEncryption.AWS_MANAGED,
            timeToLiveAttribute: 'ttl',
            pointInTimeRecoverySpecification: {pointInTimeRecoveryEnabled: false}
        });

        const readScaling = this.dynamoTable.autoScaleReadCapacity({
            minCapacity: 1,
            maxCapacity: 10
        });

        const writeScaling = this.dynamoTable.autoScaleWriteCapacity({
            minCapacity: 1,
            maxCapacity: 10
        });

        readScaling.scaleOnUtilization({
            targetUtilizationPercent: 70
        });

        writeScaling.scaleOnUtilization({
            targetUtilizationPercent: 70
        });
    }
}