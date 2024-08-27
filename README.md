# Ambient Weather Tracking

## Overview
This program is designed to track and archive data from an ambient weather station by interfacing with the Ambient Weather API. It fetches and records weather statistics, storing them in a Google Sheet for historical tracking. Data is archived at hourly intervals, starting from when the program is first initiated for a specified weather station. Additionally, the program calls the Ambient Weather API every 15 minutes, allowing for future development of features such as controlling smart home systems based on weather data. The application is containerized using Docker and deployed via Kubernetes on AWS EKS, ensuring continuous 24/7 data collection, with log monitoring and alerts managed through AWS CloudWatch.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Environment Setup](#environment_setup)
- [Prerequisites](#prerequisites)
- [Setup](#setup)
- [Troubleshooting](#troubleshooting)
- [Logs and Monitoring](#logs_and_monitoring)
- [Security Considerations](#securityconsiderations)
- [Cleanup](#cleanup)
- [Examples](#examples)
- [Diagrams](#diagrams)

## Features

- **Data Retrieval:**
   - Retrieves weather data from the Ambient Weather Station using the Ambient Weather API, ensuring accurate and up-to-date information is collected.
- **Data Storage:**
   - Writes the collected weather data to a Google Sheet via the Google Cloud API, providing a simple and accessible way to view and analyze the data. 
   - Automatically creates a new sheet each year to organize and store data for long-term historical tracking. 
   - Ensures data is accurately placed in the correct row within the Google Sheet, enabling effective use for various analytical purposes.
- **Security:**
   - Accesses API credentials, MAC Addresses, and Sheet IDs securely through Kubernetes Secrets, protecting sensitive information from unauthorized access.
- **Containerization:**
  - The program is containerized using Docker, ensuring consistent and reliable operation across different environments, from development to production.
- **Deployment and Scalability:**
  - Deploys the containerized program via Kubernetes, allowing for seamless management, scaling, and updates as needed to meet changing demands.
- **Error Handling and Logging**:
   - Implements robust error handling and logging mechanisms to prevent crashes and manage API-related errors automatically, ensuring continuous and reliable operation.
- **High Availability:**
   - Hosts the application on AWS EKS, providing a scalable and reliable infrastructure that enables continuous 24/7 data collection with high availability.
- **Monitoring and Alerts:**
   - Utilizes AWS CloudWatch for log monitoring and alerting, ensuring timely responses to potential issues and maintaining system health.


## Environment Setup
Before you begin, ensure you have the following tools installed and configured:

1. **Java JDK 17**: Required to build and run the program.
2. **Maven**: For managing project dependencies and building the project.
3. **Docker**: For containerizing the application.
4. **Kubernetes**: For managing containerized applications in a cluster.
5. **AWS CLI**: To interact with AWS services, including EKS and ECR.
6. **IDE**: Any IDE of your choice

## Prerequisites
Before you begin, ensure you have the following set up and configured:
1. **Google Cloud Project**
   - A Google Cloud project with the Google Sheets API enabled 
   - A Service Account with both edit and read permissions for the Google Cloud API. 
   - Service Account Credentials: Download the JSON credentials file for your service account, which will be used to authenticate your application.
2. **Ambient Weather Account**
   - Ambient Weather API Key: Required to access the Ambient Weather API. 
   - Ambient Weather Application Key: This key is needed alongside the API Key to authenticate API requests. 
   - MAC Address: The MAC address of your personal Ambient Weather Machine, which is used to retrieve weather data.
3. **AWS**
   - Set up with Elastic Kubernetes Service (EKS) for running the program in a scalable and managed environment
     Set up with Elastic Container Registry (ECR) for storing and managing Docker images that will be deployed to your EKS cluster.
   
## Setup

### Google Sheets API Setup
1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Create a new project or select an existing one.
3. Enable the Google Sheets API for your project.
4. Create a service account with the necessary permissions (Editor and Viewer access for the Project).
5. Download the JSON credentials file for the service account.
6. Store the credentials in Kubernetes as a secret (as described in the later setup steps).

### Docker, AWS ECR, and AWS EKS Setup
1. Install the repository and set up the program in Maven with Java JDK 17 in your preferred IDE
2. Build Maven Dependency: `mvn clean package`
3. Enable Kubernetes in Docker Desktop settings, kubectl should automatically configure itself to use Docker Desktop Kubernetes Cluster
4. Build the Docker Image: `docker build -t <CONTAINER_NAME> .`
5. Authenticate Docker to ECR (Amazon Elastic Container Registry): `aws ecr get-login-password --region <INSERT_REGION> | docker login --username AWS --password-stdin<IMAGE_URI>`
6. Tag the Docker Image to push to AWS ECR: `docker tag <CONTAINER_NAME>:latest <IMAGE_URI>/<ECR_NAME>:<BUILD_NAME>`
7. Push the Docker Image to AWS ECR: `docker push <IMAGE_URI>/<ECR_NAME>:<BUILD_NAME>`
8. Configure kubectl to Use EKS Cluster: `aws eks --region <region> update-kubeconfig --name <cluster-name>`
9. Store Secrets in Kubernetes
   - These secrets are required for the application to interact with the Ambient Weather API, Google Sheets API, and for managing the credentials in a secure manner.
       1. Ambient Weather API Key: `kubectl create secret generic api-key-secret --from-literal=api-key=<YOUR_API_KEY>`
       2. Ambient Weather Application: `kubectl create secret generic app-key-secret --from-literal=app-key=<YOUR_APP_KEY>`
       3. Weather Station MAC Address: `kubectl create secret generic mac-add-secret --from-literal=mac-add=<YOUR_MAC_ADD>`
       4. Google Spreadsheet ID: `kubectl create secret generic spreadsheet-id-secret --from-literal=spreadsheet-id=<YOUR_MAC_ADD>`
   - Ensures that the `GOOGLE_APPLICATION_CREDENTIALS` environment variable points to the correct path inside the container where the Google service account credentials are mounted.
       1. Google Service Account Credential: `kubectl create secret generic google-credentials-secret --from-file=credentials.json=<path/to/your/credentials.json>`
10. Deploy to Kubernetes Cluster:
    - Use the provided YAML file to deploy the application: `kubectl apply -f server-deployment.yaml`
    - This command will create and manage the resources defined in the `server-deployment.yaml` file, including pods, services, and secrets.
11. Verify Deployment:
    - Check the status of the pods: `kubectl get deployments`
    - View logs to ensure the application is running correctly: `kubectl describe deployment <deployment-name>`

## Troubleshooting
- **Issue**: `403 Forbidden` error when accessing Google Sheets.
    - **Solution**:Verify that the service account has the necessary permissions to access the Google Sheet. Also, ensure that the Google Sheet is shared with the service account's email address
- **Issue**: `429 Rate Limit Exceeded` error when the Ambient API is called too frequently
  - **Solution**: Ensure there is sufficient time between API calls, ideally around 5 seconds, to avoid exceeding the rate limit
- **Issue**: `401 Invalid API or Application Key` error when Ambient Weather API or Application Key are invalid or not yet approved 
  - **Solution**: Confirm that both the API Key and Application Key are correct and belong to the same account. Note that the Application Key may take a few days to be approved by Ambient Weather

## Logs and Monitoring
- The application logs are forwarded to AWS CloudWatch for centralized monitoring.
- To view logs, go to the CloudWatch Console and navigate to the log group associated with your EKS cluster.
- Set up CloudWatch Alarms to monitor for specific log events (e.g., error messages) and trigger alerts when necessary.

## Security Considerations
- **Kubernetes Secrets**: Ensure that sensitive information, such as API keys and credentials, is stored securely in Kubernetes Secrets.
- **Google Service Account Key Rotation**: Regularly rotate your Google Service Account Key to minimize security risks.
- **AWS & Google Service Account IAM Policies**: Apply the principle of least privilege to your AWS and Google Cloud IAM roles and policies to ensure that the application only has access to the resources it needs.


## Cleanup
- **Remove** the Kubernetes resources created during this setup
  - `kubectl delete -f server-deployment.yaml`

- **Remove** the Docker images from your local machine:
  - `docker rmi <IMAGE_URI>/<ECR_NAME>:<BUILD_NAME>`

## Examples
Example output to the Google Sheet

![Screenshot 2024-08-26 at 7 10 35 PM](https://github.com/user-attachments/assets/8abd9253-1d83-4389-923c-d190f139928c)

Example Log Output

![Screenshot 2024-08-26 at 10 21 39 PM](https://github.com/user-attachments/assets/2b479fd2-2f47-4816-9472-aaec92a1e88d)

## Diagrams
Diagram illustrating the basic architecture of the program, including potential future enhancements such as integrating databases like AWS S3 and SQL to work in conjunction with Google Sheets.

![Screenshot 2024-08-26 at 10 20 27 PM](https://github.com/user-attachments/assets/02e744d8-d89a-445a-bd5c-c3655a6417fa)


