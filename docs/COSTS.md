# AWS cost worksheet

Reviewed **23 November 2026**. These are planning estimates in USD, before tax, for **US East (Northern Virginia), Linux/Unix On-Demand**, using **730 hours/month** unless a row says otherwise. Prices vary by region and can change. No AWS account or paid resource was created to obtain these estimates. Recheck the official pages and [AWS Pricing Calculator](https://calculator.aws/) before deployment. Credits, promotions and Free Tier eligibility are not assumed.

## Single-host sandbox

| Item | Rate / assumption | Approximate monthly amount |
| --- | --- | ---: |
| EC2 `t3.medium`, 2 vCPU / 4 GiB | Official T3 page displayed $0.0418/hour in us-east-1 | $30.51 |
| One public IPv4 address | $0.005/hour × 730 | $3.65 |
| 30 GB gp3 EBS | Illustrative $0.08/GB-month × 30; confirm the region's selected rate | $2.40 |
| One standard Route 53 hosted zone, optional | First 25 standard zones: $0.50/zone-month | $0.50 |
| **Illustrative subtotal** | Compute, one IPv4, assumed storage and optional DNS | **$37.06** |

This is not an all-inclusive quote. It excludes backups/snapshots, DNS queries, domain registration, internet egress, logs/metrics, image storage, taxes and possible CPU-credit charges. T3 is burstable: sustained CPU use in Unlimited mode can add charges. Set an AWS Budget and inspect real usage rather than treating this subtotal as a cap.

A `t3.small` has 2 GiB and the same official page displayed $0.0209/hour, approximately **$15.26/month** before the other items. This is a cost option to evaluate after measuring JVM/database memory. It is not the recommended untested minimum. Stopping an instance stops its compute charge, but retained EBS volumes, snapshots, and allocated IPv4 addresses may continue to cost money.

## Optional services

| Service | How to account for it |
| --- | --- |
| ECR | Official private-image storage examples use **$0.10/GB-month**. Retain a small number of reviewed images; storage, transfer and scanning options can affect the bill. |
| Secrets Manager | Official examples use **$0.40/secret-month** plus **$0.05/10,000 API calls**. Three secrets would be about $1.20/month before calls at those rates. |
| Route 53 queries | Standard public DNS queries start at **$0.40/million** in the published rate tier. Domain registration is separate. |
| RDS PostgreSQL | Price the chosen engine/version, instance, region, Single-/Multi-AZ, storage, backups and I/O in the calculator. No unverified fixed RDS number is inserted here. |
| CloudWatch | Specify ingestion GB, retention days, metric/alarm counts and query usage. A log-volume budget is more useful than assuming logging is free. |
| S3 backups | Estimate retained dump size, number of copies, storage class, requests and retrieval. Enable lifecycle expiry for sandbox backups. |
| ECS/Fargate and ALB | Add vCPU/memory running hours, load balancer hours/LCUs and any public IPv4/networking charges. This is not included in the single-host subtotal. |
| CloudFront | Price delivered bytes/requests and any origin costs for the expected audience. It is optional for this small app. |

Avoid adding a NAT gateway merely to match an architecture diagram; it has hourly and data-processing charges. A more resilient private-network architecture may justify it, but that decision should be explicit.

## Official sources

- [EC2 T3 instance specifications and published regional price table](https://aws.amazon.com/ec2/instance-types/t3/)
- [Public IPv4 pricing](https://aws.amazon.com/vpc/pricing/)
- [EBS gp3 billing and worked examples](https://aws.amazon.com/ebs/pricing/)
- [Route 53 hosted zones and query pricing](https://aws.amazon.com/route53/pricing/)
- [ECR pricing](https://aws.amazon.com/ecr/pricing/)
- [Secrets Manager pricing](https://aws.amazon.com/secrets-manager/pricing/)
- [RDS PostgreSQL pricing](https://aws.amazon.com/rds/postgresql/pricing/)
- [Fargate pricing](https://aws.amazon.com/fargate/pricing/)
- [CloudWatch pricing](https://aws.amazon.com/cloudwatch/pricing/)
- [S3 pricing](https://aws.amazon.com/s3/pricing/)
- [CloudFront pricing](https://aws.amazon.com/cloudfront/pricing/)

The EBS line is explicitly an assumed unit rate from AWS's example, not a claim that every region charges that amount. All totals are rounded arithmetic, not an AWS bill or a deployment benchmark.
