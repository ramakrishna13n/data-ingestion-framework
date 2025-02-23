import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as cr from 'aws-cdk-lib/custom-resources';
import {Construct} from 'constructs';

interface OpenSearchVpcEndpointProps extends cdk.StackProps {
    vpc: ec2.Vpc;
    openSearchSecurityGroup: ec2.SecurityGroup;
}

export class OpenSearchVpcEndpointStack extends cdk.Stack {
    public readonly vpcEndpointId: string;

    constructor(scope: Construct, id: string, props: OpenSearchVpcEndpointProps) {
        super(scope, id, props);

        const {vpc, openSearchSecurityGroup} = props;

        const opensearchVpcEndpoint = new cr.AwsCustomResource(this, 'OpenSearchVpcEndpointCR', {
            onCreate: {
                service: 'OpenSearchServerless',
                action: 'createVpcEndpoint',
                parameters: {
                    name: 'opensearch-endpoint',
                    vpcId: vpc.vpcId,
                    subnetIds: vpc.selectSubnets({subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS}).subnetIds,
                    securityGroupIds: [openSearchSecurityGroup.securityGroupId],
                    clientToken: 'myClientToken123',
                },
                physicalResourceId: cr.PhysicalResourceId.of('OpenSearchVpcEndpoint'),
            },
            policy: cr.AwsCustomResourcePolicy.fromSdkCalls({resources: cr.AwsCustomResourcePolicy.ANY_RESOURCE}),
        });

        this.vpcEndpointId = opensearchVpcEndpoint.getResponseField('createVpcEndpointDetail.id');

        new cdk.CfnOutput(this, 'OpenSearchVpcEndpointIdOutput', {
            value: this.vpcEndpointId,
            description: 'OpenSearch VPC Endpoint ID',
        });
    }
}
