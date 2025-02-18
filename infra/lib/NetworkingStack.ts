import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';

interface NetworkingStackProps extends cdk.StackProps {
    maxAzs?: number;
    natGateways?: number;
}

export class NetworkingStack extends cdk.Stack {
    public readonly vpc: ec2.Vpc;

    constructor(scope: cdk.App, id: string, props?: NetworkingStackProps) {
        super(scope, id, props);

        this.vpc = new ec2.Vpc(this, 'VPC', {
            maxAzs: 2,
            natGateways: 0,
            subnetConfiguration: [
                {
                    name: 'Private',
                    subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS
                },
                {
                    name: 'Isolated',
                    subnetType: ec2.SubnetType.PRIVATE_ISOLATED
                }
            ],
        });
    }
}