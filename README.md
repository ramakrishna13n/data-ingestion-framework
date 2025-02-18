# AWS Stock Data Processing Pipeline

## Overview
This project implements a scalable data ingestion and querying system designed to efficiently process, store and analyze large datasets using AWS Redshift and OpenSearch. 
The system follows a batch ingestion approach allowing analytical data to be loaded into Redshift and OpenSearch while enabling real-time queries via AWS AppSync (GraphQL).

## 📜 **Architecture Diagram**
![Ingestion App Architecture Diagram.png](Ingestion%20App%20Architecture%20Diagram.png)

## 🏗 **Architecture Components**
- **Amazon S3** → Stores stock data files.
- **AWS Lambda (Batch Trigger Lambda)** → Listens for S3 events and triggers AWS Batch.
- **AWS Batch** → Processes stock data and hydrates:
    - **Amazon Redshift** (for analytical queries)
    - **Amazon OpenSearch** (for fast search and filtering)
- **Amazon OpenSearch (Private Subnet)** → Stores processed stock data for real-time queries.
- **VPC Endpoint (Public Subnet)** → Allows AWS Batch and AppSync to securely access OpenSearch.
- **AWS AppSync** → Provides a **GraphQL API**:
    - Queries **Redshift via a resolver lambda
    - Queries **OpenSearch via VTL Queries
