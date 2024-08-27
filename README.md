# Ambient Weather Tracking

## Overview
This program is designed to track and archive data from an ambient weather station by interfacing with the Ambient Weather API. It fetches and records weather statistics, storing them in a Google Sheet for historical tracking. Data is archived at hourly intervals, starting from when the program is first initiated for a specified weather station. Additionally, the program calls the Ambient Weather API every 15 minutes, allowing for future development of features such as controlling smart home systems based on weather data. The application is containerized using Docker and deployed via Kubernetes on AWS EKS, ensuring continuous 24/7 data collection, with log monitoring and alerts managed through AWS CloudWatch.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Prerequisites](#prerequisites)
- [Setup](#setup)
- [Usage](#usage)
- [Examples](#examples)
- [Diagrams](#diagrams)
- [License](#license)

## Features

- Retrieves data from the Ambient Weather Station using the Ambient Weather API.
- Writes collected data to a Google Sheet.
- Automatically creates a new sheet yearly to organize and store new data.
- Ensures data is placed in the correct row, allowing for effective use within the Google Sheet for various purposes.
- Accesses API credentials securely through Kubernetes Secrets, protecting sensitive information.
- The program is containerized using Docker, ensuring consistent and reliable operation across different environments.
- Deploys the containerized program via Kubernetes, allowing for seamless management and easy scaling as needed.
- Implements robust error handling and logging mechanisms to prevent crashes and automatically manage API-related errors, ensuring continuous and reliable operation.
- Hosts the application on AWS EKS, providing a scalable and reliable infrastructure that enables continuous 24/7 data collection with high availability.
- Utilizes AWS CloudWatch for log monitoring and alerting, ensuring timely responses to potential issues.

## Prerequisites
- Java JDK 17 or later installed on your machine.
- Maven installed for dependency management.
- A Google Cloud project with the Google Sheets API enabled.
- OAuth 2.0 credentials (client ID and secret) for accessing the Google Sheets API.
- Ambient Weather Application and API Key to access the Ambient Weather API.
- MAC Address for your personal Ambient Weather Machine to retrieve data from.
- Docker installed for containerizing the application.
- Kubernetes installed or access to a Kubernetes cluster for deploying the containerized application.
- An AWS account with EKS (Elastic Kubernetes Service) set up for running the program in a scalable and managed environment.

## Setup


## Usage

## Example
Example output to the Google Sheet

![Screenshot 2024-08-26 at 7 10 35 PM](https://github.com/user-attachments/assets/0536f3a3-f371-473a-b9e5-a8d5c672fdf2)

## Diagrams
Diagram illustrating the basic architecture of the program, including potential future enhancements such as integrating databases like AWS S3 and SQL to work in conjunction with Google Sheets.

![Screenshot 2024-08-26 at 5 13 33 PM](https://github.com/user-attachments/assets/3772aa59-9c9f-4b2a-8123-e61080b5f808)
