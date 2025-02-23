# AWS Stock Data Processing Pipeline

## Overview
This project implements a scalable data ingestion and querying system designed to efficiently process, store, and analyze large datasets using AWS Redshift and OpenSearch. The system follows a batch ingestion approach allowing analytical data to be loaded into Redshift and OpenSearch while enabling real-time queries via AWS AppSync (GraphQL).

For this Proof of Concept (PoC), I used the **AWS Open Data** available on the [AWS Marketplace](https://aws.amazon.com/marketplace/seller-profile?id=c3d0a604-a811-4af5-bdba-7a9976bc0d0b). This free dataset contains 20 years of historical data for the top 10 US stocks by market capitalization as of September 5, 2020. The data is a subset of historical market data from the [Alpha Vantage](https://aws.amazon.com/marketplace/seller-profile?id=c3d0a604-a811-4af5-bdba-7a9976bc0d0b). To use this dataset:
1. Subscribe to it on AWS Marketplace
2. Copy the data to your `<S3 Bucket>/stock-data` ** before running the ingestion pipeline.

## **Architecture Diagram**
![Ingestion App Architecture Diagram.png](Ingestion%20App%20Architecture%20Diagram.png)

## **Architecture Components**
- **Amazon S3** → Stores stock data files.
- **AWS Lambda (Batch Trigger Lambda)** → Listens for S3 events and triggers AWS Batch.
- **AWS Batch** → Processes stock data and hydrates:
  - **Amazon Redshift** (for analytical queries)
  - **Amazon OpenSearch** (for fast search and filtering)
- **Amazon OpenSearch (Private Subnet)** → Stores processed stock data for real-time queries.
- **VPC Endpoint (Public Subnet)** → Allows AWS Batch and AppSync to securely access OpenSearch.
- **AWS AppSync** → Provides a **GraphQL API**:
  - Queries **Redshift via a resolver lambda**
  - Queries **OpenSearch via VTL Queries**

## **Cost Optimization Considerations**
- **Redshift Serverless** is used to **minimize costs** while maintaining scalable performance.
- **OpenSearch is deployed in a private VPC**, reducing unnecessary public access charges.
- **S3 is used for staging and as a cost-effective storage solution.**
- **Batch processing minimizes compute costs** by running workloads only when needed.

**IMPORTANT:** Please **delete all deployed stacks** after testing to avoid incurring unnecessary AWS costs.
```
npx cdk destroy
```

## Product Versions
- AWS CDK v2.178.2
- Amazon Corretto v17, distribution of Open JDK

## Prerequisite
- Install `Amazon Corretto 17` using the OS-based instructions outlined [here](https://docs.aws.amazon.com/corretto/latest/corretto-17-ug/what-is-corretto-17.html) if not already installed.
- AWS Account with AWS Identity and Access Management (IAM) permissions to access AWS CloudFormation, AWS IAM, and create necessary services.
- AWS CDK Toolkit
  If it is not already installed, install it using the following command:
    ```
    npm install -g aws-cdk
    ```
  If AWS CDK Toolkit version is earlier than 2.178.2, then enter the following command to update it to version 2.178.2:
    ```
    npm install -g aws-cdk@2.178.2 --force
    ```

## Setup
- Clone the repository on your local machine.
```
git clone <<repository url>>
```
- On the terminal window, navigate to the `cdk` folder and install CDK dependencies:
```
cd cdk
npm install
```
- Export environment variables required for the CDK stack:
```
export AWS_REGION=<<region>>
export AWS_ACCOUNT=<<account>>
```

## Deploy
- Configure AWS credentials.
- The following step is required for the first time to bootstrap the CDK environment. This step is optional for successive deployments.
```
npx cdk bootstrap
```
- Deploy the AWS resources using the `data-ingestion-framework/infra` CDK stack to your AWS account:
```
npx cdk deploy
```
