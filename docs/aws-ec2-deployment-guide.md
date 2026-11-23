# YFC Transport ERP — Complete AWS EC2 + RDS PostgreSQL 17 + S3 Deployment Guide

> **Scope:** End-to-end production deployment of YFC Transport ERP (Spring Boot 4.1.1 / Java 21 backend + React 19 / Vite frontend) into a fresh/target AWS account using **Amazon EC2 (Ubuntu 24.04 LTS + Nginx + native Java 21 `systemd` service)**, a **brand-new managed Amazon RDS for PostgreSQL 17 database** inside that same AWS account, and a **private Amazon S3 bucket** for document uploads.
>
> Every single step explains **what** to do, **how** to do it (exact Console settings and commands), **why** each flag/setting is chosen, and **why not** the common alternatives.

---

## Table of Contents

1. [Architecture Overview & Design Reasoning (Why & Why Not)](#1-architecture-overview--design-reasoning-why--why-not)
2. [Prerequisites & Local Machine Checklist](#2-prerequisites--local-machine-checklist)
3. [Phase 1 — AWS Networking: VPC, Subnets & Security Groups](#3-phase-1--aws-networking-vpc-subnets--security-groups)
4. [Phase 2 — Amazon S3 Bucket & EC2 IAM Instance Role](#4-phase-2--amazon-s3-bucket--ec2-iam-instance-role)
5. [Phase 3 — Brand-New Amazon RDS PostgreSQL 17 Database](#5-phase-3--brand-new-amazon-rds-postgresql-17-database)
6. [Phase 4 — Launching & Hardening the EC2 Instance + Elastic IP + DNS](#6-phase-4--launching--hardening-the-ec2-instance--elastic-ip--dns)
7. [Phase 5 — One-Time EC2 Server Setup (Every Command & Directive Explained)](#7-phase-5--one-time-ec2-server-setup-every-command--directive-explained)
8. [Phase 6 — Building the Backend JAR & Frontend Bundle Locally](#8-phase-6--building-the-backend-jar--frontend-bundle-locally)
9. [Phase 7 — Deploying Artifacts to EC2 & Fast-Rollback Workflow](#9-phase-7--deploying-artifacts-to-ec2--fast-rollback-workflow)
10. [Phase 8 — First Boot, Full Verification & Mandatory Security Cleanup](#10-phase-8--first-boot-full-verification--mandatory-security-cleanup)
11. [Phase 9 — Day-2 Operations, Backups, Data Migration & Troubleshooting](#11-phase-9--day-2-operations-backups-data-migration--troubleshooting)
12. [Appendix A — Complete Environment Variable Reference](#12-appendix-a--complete-environment-variable-reference)
13. [Appendix B — Switching RDS from Password Auth to IAM Auth Later](#13-appendix-b--switching-rds-from-password-auth-to-iam-auth-later)
14. [Appendix C — Automated CI/CD Auto-Deploy via GitHub Actions (S3 + AWS SSM + OIDC)](#14-appendix-c--automated-cicd-auto-deploy-via-github-actions-s3--aws-ssm--oidc)

---

## 1. Architecture Overview & Design Reasoning (Why & Why Not)

### 1.1 Production Request & Data Flow

```text
Internet Users (Staff ERP, Customers, Suppliers, Drivers, Public Website)
        │
        │  HTTPS (TCP 443) to erp.yourdomain.com
        │  (HTTP TCP 80 automatically 301-redirects to HTTPS 443)
        ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  AWS VPC (Single Region, e.g. ap-south-1 Mumbai or ap-south-2 Hyderabad)│
│                                                                         │
│  ┌────────────────────────── Public Subnet ──────────────────────────┐  │
│  │  EC2 Instance (Ubuntu 24.04 LTS, t4g.small / t3.small, 2 GB RAM)  │  │
│  │  Attached: Elastic IP + SG (yfc-ec2-sg) + IAM Role (yfc-ec2-role) │  │
│  │                                                                   │  │
│  │  ┌─────────────────────────────────────────────────────────────┐  │  │
│  │  │ Nginx (:80 / :443, Let's Encrypt TLS via Certbot)           │  │  │
│  │  │  • /assets/*, /media/*, /* ──► /var/www/yfc (React SPA)     │  │  │
│  │  │  • /api/*, /login, /logout ──► http://127.0.0.1:8080        │  │  │
│  │  └──────────────────────────┬──────────────────────────────────┘  │  │
│  │                             │ Loopback only (never public!)       │  │
│  │                             ▼                                     │  │
│  │  ┌─────────────────────────────────────────────────────────────┐  │  │
│  │  │ systemd: yfc-backend (User: yfc, Java 21 JRE, 127.0.0.1:8080)│  │  │
│  │  │  • Reads secrets from /etc/yfc/yfc.env (root-only 0600)     │  │  │
│  │  │  • Runs Flyway V1–V27 migrations automatically on startup   │  │  │
│  │  │  • Stores sessions in PostgreSQL (persistent_sessions)      │  │  │
│  │  │  • Signs S3 requests using EC2 IAM Role (IMDSv2)            │  │  │
│  │  └──────────────┬─────────────────────────────┬────────────────┘  │  │
│  └─────────────────┼─────────────────────────────┼───────────────────┘  │
│                    │                             │                      │
│  TLS verify-full   │ TCP 5432                    │ HTTPS 443 (SigV4)    │
│  (private VPC)     ▼                             ▼                      │
│  ┌─────── Private DB Subnets (2 AZs) ──────┐   ┌─────────────────────┐  │
│  │ Amazon RDS for PostgreSQL 17            │   │ Amazon S3 (Private) │  │
│  │ SG: yfc-rds-sg (allows ONLY yfc-ec2-sg) │   │ Bucket:             │  │
│  │ Public access: NO                       │   │ yfc-prod-documents-…│  │
│  │ Initial database: yfc                   │   │ Prefix:             │  │
│  │ Automated backups + KMS encryption      │   │ yfc-documents/      │  │
│  └─────────────────────────────────────────┘   └─────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Architectural Decisions — Why This Stack & Why Not Alternatives

Understanding *why* each choice was made will prevent accidental misconfigurations later.

#### A. Why Managed Amazon RDS PostgreSQL 17 instead of installing PostgreSQL on the EC2 instance?
- **Why RDS:**
  - **Automated backups & Point-in-Time Recovery (PITR):** RDS takes daily automated snapshots and continuous WAL archives so you can restore the ERP accounting/billing database to any second in the retention window.
  - **Failure isolation:** If your EC2 instance crashes, runs out of disk space, or needs OS replacement, your financial and operational data in RDS is completely untouched.
  - **Resource isolation:** PostgreSQL's shared buffers, vacuum workers, and disk I/O do not compete with the JVM heap on your 2 GB EC2 server.
  - **Security:** RDS lives in private subnets with zero public IP, encrypted EBS volumes, and TLS enforced via AWS CA certificates.
  - **Easy future growth:** Upgrading instance size or turning on Multi-AZ standby is a few clicks with no manual replication setup.
- **Why NOT PostgreSQL installed locally on EC2:**
  - You must manually build, test, encrypt, and offload `pg_dump` backups to S3.
  - A full EC2 root disk (from logs or deployments) can corrupt or halt PostgreSQL.
  - Running Java 21 + Nginx + PostgreSQL on a single 2 GB machine causes constant RAM pressure and slow queries.
- **Why NOT keeping the previous Aurora Express cluster (`backend/.env.aws`):**
  - You explicitly want a fresh PostgreSQL database owned inside your target AWS account. The previous `backend/.env.aws` and `backend/scripts/aws-db.sh` helpers were built specifically for developer workstation sign-in (`aws login`) into that old Aurora Express gateway. They are **not** used in this EC2 production deployment.

#### B. Why Native Java 21 JAR + `systemd` on EC2 instead of Docker on EC2 or ECS Fargate?
- **Why native JAR + `systemd`:**
  - **No container runtime overhead:** On a 2 GB EC2 instance (`t4g.small` / `t3.small`), skipping Docker daemon saves RAM, disk space (no dangling image layers filling the root disk), and operational complexity.
  - **First-class OS integration:** Linux `systemd` automatically starts the app on reboot, restarts it on crash, enforces graceful shutdown (`SIGTERM`), injects root-only environment files securely, and streams logs straight to `journalctl` with rotation.
  - **Fast, simple deployments:** A Spring Boot executable JAR is a single ~85 MB file. Copying it over SSH and restarting `systemd` takes seconds and is platform-independent (the exact same JAR runs on x86_64 and ARM64).
- **Why NOT building code (`mvn` / `npm`) directly on EC2:**
  - Maven and Vite/TypeScript builds spike CPU and RAM (often exceeding 2 GB RAM and causing OOM kills on a live server).
  - Keeping compilers, Git credentials, and source code off the production server reduces attack surface and disk usage.

#### C. Why Same-Origin Nginx Reverse Proxy instead of separate API/frontend domains?
- Look directly at how this codebase works:
  - `frontend/src/api.ts` sends every HTTP call to relative URLs (`/api/v1/...`, `/login`, `/logout`) with `credentials: "same-origin"`.
  - `backend/src/main/java/com/yfctransport/identity/SecurityConfiguration.java` intentionally does **not** configure CORS, and relies on `HttpOnly` + `SameSite=Lax` + `Secure` cookies (`JSESSIONID` and `YFC_SESSION`) paired with Spring Security's CSRF token (`/api/v1/csrf`).
- **Why Same-Origin Nginx:**
  - Browser sees one origin (`https://erp.yourdomain.com`). Cookies and CSRF work natively without relaxing `SameSite=None` or maintaining CORS allowlists.
  - Nginx serves static files (`/var/www/yfc`) directly from C/epoll with minimal CPU and adds long-term cache headers for Vite's hashed `/assets/*`.
  - Nginx terminates HTTPS once (using free auto-renewed Let's Encrypt certificates) and talks plain HTTP over local loopback (`127.0.0.1:8080`) to Spring Boot.
- **Why NOT exposing Spring Boot directly on port 80/443:**
  - Binding Java to privileged ports (`< 1024`) requires extra capabilities or root privileges.
  - Spring Boot does not have a `static/` directory in this repository (`backend/src/main/resources/static` does not exist), so something still has to serve `frontend/dist`.
  - Managing TLS certificate renewal directly inside Java JKS/PKCS12 keystores is fragile compared to Nginx + Certbot.

#### D. Why Amazon S3 (via IAM Role) for Private Documents instead of PostgreSQL `BYTEA` or EC2 Local Disk?
- In `backend/src/main/java/com/yfctransport/documents/DocumentBlobRouter.java`, YFC supports three storage backends: `DATABASE`, `LOCAL`, and `S3`.
- **Why `S3`:**
  - Uploaded trip proofs, fuel bills, and incident receipts (up to 2 MiB each, max 30 per record per `DocumentService.java`) do not bloat PostgreSQL tables, indexes, WAL logs, or RDS backup size.
  - S3 provides 11 nines of durability and costs pennies per GB/month.
  - `S3DocumentBlobStore.java` signs requests natively with AWS SigV4 using `DefaultCredentialsProvider`, which automatically picks up the EC2 IAM Instance Role—**zero long-lived AWS access keys exist on disk**.
- **Why NOT `LOCAL` (EC2 filesystem):**
  - Files stored on the EC2 root volume are not included in RDS backups and are lost if the EC2 instance is terminated or replaced.
- **Why NOT `DATABASE` (the default for local dev):**
  - Fine for local development or small demos, but binary PDFs/images inside PostgreSQL increase RDS storage cost and slow down dumps/restores over time. *(Note: if you ever migrate legacy rows that were stored in `DATABASE`, `DocumentBlobRouter.java` supports hybrid reads automatically—old rows read from DB while new uploads go to S3.)*

#### E. Why HTTPS + a Real Domain is Mandatory for `SPRING_PROFILES_ACTIVE=prod`
- `backend/src/main/resources/application-prod.yml` sets:
  ```yaml
  server:
    forward-headers-strategy: native
    servlet:
      session:
        timeout: 30m
        cookie:
          secure: true
  ```
- `PersistentSessionStore.java` also sets the `Secure` attribute on `YFC_SESSION` based on `request.isSecure()`.
- **What happens if you try plain HTTP (`http://<ec2-ip>`) with `prod`:** The browser refuses to store or send `Secure` cookies over non-HTTPS connections, so `/api/v1/csrf` and `/login` will fail silently with `403`/`401` loops.
- **Why NOT disabling `secure: true`:** Without HTTPS, staff passwords, session tokens, GST invoices, and party financial ledgers travel in plaintext over the internet.

---

## 2. Prerequisites & Local Machine Checklist

Before starting, make sure you have:

1. **Target AWS Account Access:**
   - Sign in using an **IAM Admin user or IAM Identity Center role** with permissions for VPC, EC2, RDS, S3, and IAM.
   - **Why NOT the AWS Root User?** AWS root credentials cannot be restricted by IAM policies and are a major security risk if compromised. Use root only to create your admin IAM user and enable MFA.
2. **One Chosen AWS Region:**
   - Pick the region closest to your users in India: **`ap-south-1` (Mumbai)** or **`ap-south-2` (Hyderabad)**.
   - **Why keep EC2, RDS, and S3 in the exact same region?** Cross-region database queries add 20–100+ ms latency per SQL statement (breaking ERP responsiveness) and incur AWS inter-region data transfer charges on every query and S3 download.
3. **A Domain Name (or Subdomain):**
   - Example: `erp.yourcompany.com` (managed in Route 53, Cloudflare, GoDaddy, Namecheap, etc.).
   - You only need the ability to add one DNS **`A` record** pointing to your EC2 Elastic IP.
   - *(If using Cloudflare DNS: set the proxy cloud to **DNS Only / Grey Cloud** while running Certbot initially so Let's Encrypt reaches Nginx directly; you can review proxy settings afterwards.)*
4. **Local Build Machine Tools (your Mac/laptop):**
   - Run these checks in your terminal to confirm your local build toolchain matches CI (`.github/workflows/backend.yml` and `.github/workflows/frontend.yml`):
     ```bash
     java -version    # Must be Java 21 (required by backend/pom.xml <java.version>21)
     mvn -version     # Maven 3.9+ using Java 21
     node -v          # Node.js 22+ (matches CI; 20+ works for Vite 7, 22 recommended)
     npm -v           # npm 10+
     ssh -V           # OpenSSH client
     rsync --version  # Used to cleanly sync frontend/dist to EC2
     ```

---

## 3. Phase 1 — AWS Networking: VPC, Subnets & Security Groups

Every AWS account comes with a **Default VPC** in every region (or you can use a dedicated custom VPC with explicit public and private subnets).

> **Best-practice note on Subnets:**
> - **Best security:** Use a VPC with **2 Public Subnets** (route table has `0.0.0.0/0 → Internet Gateway`, where EC2 lives) and **2 Private Subnets** (no route to Internet Gateway, where RDS lives).
> - **If using the Default VPC:** Even though Default VPC subnets have an Internet Gateway route, setting **RDS Public access = No** (no public IP assigned) + **SG allowing only `yfc-ec2-sg`** still prevents any direct internet access to RDS. However, a dedicated VPC with true private subnets is cleaner—AWS's **VPC → Create VPC → "VPC and more"** wizard creates a production-grade 2-AZ public+private VPC in ~60 seconds!

### 3.1 Optional (Recommended): Create a Clean VPC via "VPC and more"

In AWS Console → **VPC → Your VPCs → Create VPC**:
- **Resources to create**: **VPC and more**
- **Name tag auto-generation**: `yfc-prod`
- **IPv4 CIDR block**: `10.0.0.0/16`
- **Number of Availability Zones (AZs)**: `2` *(RDS requires a DB Subnet Group spanning at least 2 AZs even for a Single-AZ database.)*
- **Number of public subnets**: `2`
- **Number of private subnets**: `2`
- **NAT gateways**: **None**
  - **Why `None`?** NAT Gateways cost ~\$32/month plus per-GB data fees. Our EC2 lives in a **public subnet** (so it talks directly to S3, Ubuntu apt repos, and Let's Encrypt via the free Internet Gateway), and RDS does not need outbound internet access. That saves ~\$380/year with zero downside for Option B.
- **VPC endpoints**: **S3 Gateway** *(Optional and **FREE**! Selecting S3 Gateway routes EC2 → S3 traffic privately inside the AWS network without even traversing the internet gateway.)*
- Click **Create VPC**.

### 3.2 Create the RDS DB Subnet Group

In AWS Console → **RDS → Subnet groups → Create DB subnet group**:
- **Name**: `yfc-prod-db-subnets`
- **Description**: `Private subnets for YFC production PostgreSQL`
- **VPC**: Select your `yfc-prod-vpc` (or Default VPC).
- **Availability Zones**: Select the 2 AZs from your VPC.
- **Subnets**: Select the **2 private subnets** (the ones named `yfc-prod-subnet-private1-...` and `yfc-prod-subnet-private2-...`).
- Click **Create**.

**Why a DB Subnet Group?** RDS uses it to know which private subnets (and AZs) it is allowed to place the database network interface into. Putting only private subnets here guarantees RDS can never accidentally receive a public route.

### 3.3 Create Security Group 1 — EC2 (`yfc-ec2-sg`)

In AWS Console → **EC2 → Security Groups → Create security group**:
- **Security group name**: `yfc-ec2-sg`
- **Description**: `Inbound web (80/443) and admin SSH for YFC EC2`
- **VPC**: Select `yfc-prod-vpc`.
- **Inbound rules**:

| Type | Protocol | Port range | Source | Why this rule exists |
|---|---|---|---|---|
| HTTP | TCP | `80` | `0.0.0.0/0` | Required for (1) redirecting `http://` visitors to `https://`, and (2) Let's Encrypt Certbot ACME HTTP-01 domain verification & auto-renewal. |
| HTTPS | TCP | `443` | `0.0.0.0/0` | Serves the live encrypted YFC website, portals, and API via Nginx. |
| SSH | TCP | `22` | **My IP** (e.g. `203.0.113.10/32`) | Allows you to deploy and administer EC2. **Why NOT `0.0.0.0/0`?** Opening port 22 to the world invites constant automated SSH brute-force scanners. *(Update this IP if your ISP IP changes, or replace SSH entirely with AWS SSM Session Manager.)* |

- **Outbound rules**: Keep default (`All traffic` to `0.0.0.0/0`).
  - **Why?** EC2 needs outbound HTTPS (`443`) to S3, Ubuntu package mirrors, and Let's Encrypt, plus outbound `5432` to RDS.
- **Why NOT open port `8080`?** Spring Boot listens only on `127.0.0.1:8080`. Exposing `8080` publicly would bypass Nginx HTTPS termination and rate-limit header sanitization.
- Click **Create security group** and note its Security Group ID (e.g. `sg-0abc123456789def0`).

### 3.4 Create Security Group 2 — RDS (`yfc-rds-sg`)

In AWS Console → **EC2 → Security Groups → Create security group**:
- **Security group name**: `yfc-rds-sg`
- **Description**: `Allows PostgreSQL 5432 ONLY from YFC EC2 security group`
- **VPC**: Select `yfc-prod-vpc`.
- **Inbound rules**:

| Type | Protocol | Port range | Source | Why this rule exists |
|---|---|---|---|---|
| PostgreSQL | TCP | `5432` | **Custom → `yfc-ec2-sg` (`sg-0abc...`)** | Only instances carrying `yfc-ec2-sg` can reach PostgreSQL over the private VPC network. |

- **Why reference `yfc-ec2-sg` instead of an IP address or CIDR?**
  - If you replace or resize EC2 later, its private IP may change, but attaching `yfc-ec2-sg` automatically grants database access without touching RDS firewall rules.
  - Referencing the EC2 *public* Elastic IP does not work for private VPC traffic anyway—EC2 connects to RDS over private RFC1918 IPs inside the VPC.
- **Why NOT `0.0.0.0/0` on port `5432`?** Even with strong passwords, exposing a financial ERP database to the public internet is an unacceptable security risk.
- Click **Create security group**.

---

## 4. Phase 2 — Amazon S3 Bucket & EC2 IAM Instance Role

### 4.1 Create the Private S3 Bucket

In AWS Console → **S3 → Buckets → Create bucket**:
- **AWS Region**: Same region as your VPC/EC2/RDS (e.g. `ap-south-2`).
- **Bucket type**: General purpose.
- **Bucket name**: Must be globally unique across all AWS accounts, e.g.:
  `yfc-prod-documents-123456789012` *(replace with your 12-digit AWS account ID)*.
- **Object Ownership**: **ACLs disabled (recommended)** — simplifies permissions so the bucket owner always owns every uploaded file.
- **Block Public Access settings**: Keep **Block *all* public access** checked (`ON`).
  - **Why?** Uploaded trip receipts, weight slips, and expense bills are **private ERP documents**. Users never fetch S3 URLs directly; they authenticate to Spring Boot (`GET /api/v1/documents/{id}/content`), which verifies RBAC permissions, writes an `audit_events` row (`DOCUMENT_DOWNLOADED`), fetches bytes from S3 privately, and streams them back.
- **Bucket Versioning**: **Enable** *(Recommended—protects against accidental overwrites or deletions; you can add an S3 Lifecycle rule later to expire noncurrent versions after 30–90 days to control cost).*
- **Default encryption**: **Server-side encryption with Amazon S3 managed keys (SSE-S3)** + **Bucket Key: Enable**.
  - **Why SSE-S3 instead of SSE-KMS?** SSE-S3 encrypts every document at rest with AES-256 at **zero extra cost** and requires no KMS key policy wiring. (Use SSE-KMS only if a compliance audit specifically demands customer-managed key rotation.)
- Click **Create bucket**.

### 4.2 Create the Least-Privilege IAM Policy

In AWS Console → **IAM → Policies → Create policy → JSON**:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "YfcDocumentsObjectAccess",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject"
      ],
      "Resource": "arn:aws:s3:::yfc-prod-documents-123456789012/yfc-documents/*"
    }
  ]
}
```
*(Replace `yfc-prod-documents-123456789012` with your exact bucket name.)*

- **Policy name**: `yfc-s3-documents-policy`
- Click **Create policy**.

**Why these exact 3 actions and nothing more?**
- Check `backend/src/main/java/com/yfctransport/documents/S3DocumentBlobStore.java`:
  - `put(...)` issues an HTTP `PUT` (`s3:PutObject`) to `yfc-documents/<target_type>/<uuid>`.
  - `get(...)` issues an HTTP `GET` (`s3:GetObject`) for the exact `storage_key` stored in PostgreSQL.
  - `deleteQuietly(...)` issues an HTTP `DELETE` (`s3:DeleteObject`) only if the database `INSERT` fails after S3 upload (rollback cleanup so no orphan objects remain).
- **Why NOT `s3:ListBucket` or `s3:*`?** The application never lists bucket contents, never modifies bucket ACLs/policies, and never deletes the bucket itself. Restricting to `yfc-documents/*` enforces strict least privilege.

### 4.3 Create the EC2 IAM Role

In AWS Console → **IAM → Roles → Create role**:
- **Trusted entity type**: **AWS service**
- **Use case**: **EC2**
- Click **Next**.
- **Permissions policies**:
  - Search and check **`yfc-s3-documents-policy`**.
  - *(Optional, recommended if you want browser/CLI shell access without opening SSH port 22: also attach AWS managed policy **`AmazonSSMManagedInstanceCore`**.)*
- Click **Next**.
- **Role name**: `yfc-ec2-role`
- **Description**: `Instance role for YFC ERP EC2 backend (S3 document storage)`
- Click **Create role**.

**Why an IAM Role instead of creating an IAM User with `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`?**
- Static keys never expire, can accidentally leak in logs/backups/env files, and require manual rotation.
- An EC2 IAM Role automatically delivers short-lived temporary credentials over the local Instance Metadata Service (**IMDSv2**), rotating them every few hours with zero human effort. `S3DocumentBlobStore.java` uses `DefaultCredentialsProvider.builder().build()`, which discovers EC2 instance role credentials automatically.

---

## 5. Phase 3 — Brand-New Amazon RDS PostgreSQL 17 Database

In AWS Console → **RDS → Databases → Create database**:

Fill every section carefully:

| Section / Field | Exact Setting | Reasoning (Why & Why Not) |
|---|---|---|
| **Creation method** | **Standard create** | **Why NOT Easy create?** Easy create hides critical settings like Initial Database Name, VPC Security Group selection, and backup configuration. |
| **Engine type** | **PostgreSQL** | YFC uses PostgreSQL-specific SQL (`BYTEA`, `TIMESTAMP WITH TIME ZONE`, `OCTET_LENGTH`, `CHECK` constraints) and `flyway-database-postgresql`. |
| **Engine version** | **PostgreSQL 17.x** (latest 17 minor) | Matches `backend/compose.yml` (`postgres:17`), CI (`.github/workflows/backend.yml`), and verified migration compatibility (`V1`–`V27`). |
| **Templates** | **Dev/Test** (or **Free tier** if eligible) to start; **Production** for Multi-AZ | Dev/Test lets you choose Single-AZ `db.t4g.micro`/`small` to keep costs low while still enabling backups and encryption. Switch to Multi-AZ when your SLA requires automatic 60-second failover. |
| **Availability and durability** | **Single-AZ DB instance** (or Multi-AZ for HA) | Single-AZ cuts RDS cost in half; Multi-AZ doubles cost but adds synchronous standby failover. |
| **DB instance identifier** | `yfc-prod-db` | Clear AWS resource name (this is NOT the SQL database name—that comes below). |
| **Master username** | `yfc` | Matches YFC convention (`application.yml` default). Avoid generic `admin`; `yfc` or `postgres` both work cleanly. |
| **Credentials management** | **Self managed** | Lets you control the password cleanly for `/etc/yfc/yfc.env`. Generate a random 24–32 character alphanumeric password (avoid `@`, `?`, `#`, `&`, `%`, `$`, `'`, `"` so it never causes URL/shell/systemd escaping bugs). |
| **DB instance class** | **Burstable classes → `db.t4g.micro`** (2 vCPU, 1 GB RAM) or **`db.t4g.small`** (2 vCPU, 2 GB RAM) | Graviton (`t4g`) is ~15–20% cheaper and faster than `t3`. `db.t4g.micro` supports ~80–110 `max_connections` (more than enough for HikariCP's pool of 10). Upgrade to `db.t4g.small` if concurrent reporting traffic grows. |
| **Storage type** | **General Purpose SSD (`gp3`)** | **Why NOT `gp2`?** `gp3` is newer, cheaper, and provides baseline 3,000 IOPS independent of volume size. |
| **Allocated storage** | `20` GiB | Plenty for years of ERP text/numeric rows when documents live in S3. |
| **Storage autoscaling** | **Enable** (Maximum threshold: e.g. `100` GiB) | **Why?** Prevents sudden production downtime if database storage fills unexpectedly. |
| **Compute resource** | **Don't connect to an EC2 compute resource** | We already created clean, explicit Security Groups (`yfc-ec2-sg` → `yfc-rds-sg`) rather than letting RDS auto-generate extra SGs. |
| **VPC** | `yfc-prod-vpc` | Must be the exact same VPC as EC2. |
| **DB subnet group** | `yfc-prod-db-subnets` | Places RDS in your private subnets. |
| **Public access** | **No** | **Critical security rule.** Never assign a public IP to a production ERP database. |
| **VPC security group** | **Choose existing → `yfc-rds-sg`** *(remove `default`!)* | Guarantees ONLY your EC2 instance can reach port `5432`. |
| **Database port** | `5432` | Standard PostgreSQL port. |
| **Database authentication** | **Password authentication** | Simplest & rock-solid for EC2 + RDS. *(If you want IAM token authentication later, see [Appendix B](#13-appendix-b--switching-rds-from-password-auth-to-iam-auth-later)—you can enable it with zero code changes.)* |
| **Initial database name** *(under Additional configuration)* | **`yfc`** | **Do not skip this!** If left blank, RDS only creates the `postgres` system database and Spring Boot startup will fail with `FATAL: database "yfc" does not exist` until you manually run `CREATE DATABASE yfc;`. |
| **Automated backups** | **Enable**; Retention: **`7`–`14` days** | Enables daily snapshots + Point-in-Time Recovery to any second. |
| **Encryption** | **Enable encryption** (default `aws/rds` KMS key) | Encrypts storage, automated backups, read replicas, and snapshots at rest. |
| **Auto minor version upgrade** | **Enable** | Applies PostgreSQL 17.x security patches automatically during your maintenance window. |
| **Deletion protection** | **Enable deletion protection** | **Why?** Prevents anyone from accidentally deleting the production database in the AWS Console without explicitly disabling protection first. |

Click **Create database**. Provisioning takes ~5–10 minutes. Once Status becomes **Available**, click `yfc-prod-db` → **Connectivity & security** and copy the **Endpoint**, e.g.:
```text
yfc-prod-db.xxxxxxxxxxxx.ap-south-2.rds.amazonaws.com
```

---

## 6. Phase 4 — Launching & Hardening the EC2 Instance + Elastic IP + DNS

### 6.1 Launch the EC2 Instance

In AWS Console → **EC2 → Instances → Launch instances**:

| Setting | Value | Reasoning (Why & Why Not) |
|---|---|---|
| **Name** | `yfc-prod-app` | Easy identification in EC2 Console and billing tags. |
| **AMI** | **Ubuntu Server 24.04 LTS (HVM)** — pick **64-bit (Arm)** if choosing `t4g.small`, or **64-bit (x86)** if choosing `t3.small` | Ubuntu 24.04 LTS gets security updates until 2029 and ships `openjdk-21-jre-headless`, `nginx`, and `certbot` straight from official apt repositories. |
| **Instance type** | **`t4g.small`** (2 vCPU, 2 GB RAM, ARM64) or **`t3.small`** (2 vCPU, 2 GB RAM, x86_64) | **Why NOT `t2.micro` / `t3.micro` (1 GB RAM)?** A Spring Boot 4 + Hibernate + Flyway + AWS SDK JVM needs ~500–900 MB RSS (heap + metaspace + thread stacks + native buffers), plus Ubuntu + Nginx (~250 MB). On 1 GB RAM, Linux's OOM killer will randomly terminate the Java process under load or during PDF/image `ImageIO` decoding (`DocumentService.java`). 2 GB RAM (`small`) gives comfortable headroom. |
| **Key pair (login)** | Create new key pair: e.g. `yfc-prod-key`, Type: **ED25519**, Format: **`.pem`** | ED25519 is stronger and shorter than RSA. Move the downloaded `.pem` file to `~/.ssh/yfc-prod-key.pem` immediately. |
| **VPC** | `yfc-prod-vpc` | Must match RDS VPC. |
| **Subnet** | `yfc-prod-subnet-public1-...` | Public subnet so the Elastic IP is reachable from the internet. |
| **Auto-assign public IP** | **Enable** | Gives immediate connectivity until Elastic IP is attached. |
| **Firewall (security groups)** | **Select existing → `yfc-ec2-sg`** | Applies our strict HTTP/HTTPS/My-IP-SSH rules. |
| **Configure storage** | **`25`–`30` GiB `gp3`** | **Why NOT the 8 GiB default?** OS + Java 21 + Nginx + 3–5 kept JAR releases (~85 MB each) + `journald` logs + swap file (2 GB) can quickly push an 8 GiB disk above 85% and halt writes. 25–30 GiB `gp3` costs ~\$2–\$2.50/mo and eliminates disk emergencies. |
| **Advanced details → IAM instance profile** | **`yfc-ec2-role`** | Grants the backend permission to upload/download documents in your private S3 bucket with no static keys. |
| **Advanced details → Metadata version** | **V2 only (token required)** | **Why IMDSv2 only?** Prevents SSRF vulnerabilities from reading EC2 IAM credentials via simple `GET http://169.254.169.254/...` requests. |
| **Advanced details → Termination protection** | **Enable** | Prevents accidental termination of your production server. |

Click **Launch instance**.

### 6.2 Allocate & Associate an Elastic IP

In AWS Console → **EC2 → Network & Security → Elastic IPs**:
1. Click **Allocate Elastic IP address** → **Allocate**.
2. Select the newly created Elastic IP → **Actions → Associate Elastic IP address**.
3. **Resource type**: Instance → select **`yfc-prod-app`** → click **Associate**.
4. Copy the Elastic IP (e.g., `18.60.xxx.xxx`).

**Why an Elastic IP?**
- A normal EC2 auto-assigned public IP **changes** whenever you stop and start the instance (for example, to resize from `t4g.small` to `t4g.medium`). Changing IPs breaks your DNS `A` record and causes downtime while DNS TTLs expire.
- An Elastic IP stays permanently yours (and is **free** while attached to a running instance).

### 6.3 Point Your Domain DNS to the Elastic IP

In your DNS provider (Route 53, Cloudflare, GoDaddy, etc.), add an **`A` record**:
- **Type**: `A`
- **Host / Name**: `erp` *(for `erp.yourdomain.com`)* — or `@` for root domain `yourdomain.com`
- **Value / Points to**: `<your-elastic-ip>`
- **TTL**: `300` seconds (5 minutes)

Verify propagation from your Mac before running Certbot later:
```bash
dig +short erp.yourdomain.com A
# Expected output: your Elastic IP (e.g., 18.60.xxx.xxx)
```

---

## 7. Phase 5 — One-Time EC2 Server Setup (Every Command & Directive Explained)

### 7.1 Connect to EC2 from Your Mac

First, restrict permissions on your downloaded private key and connect:
```bash
mv ~/Downloads/yfc-prod-key.pem ~/.ssh/yfc-prod-key.pem
chmod 400 ~/.ssh/yfc-prod-key.pem
ssh -i ~/.ssh/yfc-prod-key.pem ubuntu@erp.yourdomain.com
```
- **Why `chmod 400`?** `400` = read-only for owner, zero permissions for group/others. OpenSSH **refuses** to use a private key with looser permissions (like `644`) because other local users could read it.
- **Why `ubuntu@`?** Official Ubuntu AMIs use `ubuntu` as the default sudo-enabled login user (Amazon Linux uses `ec2-user`).

---

### 7.2 Update OS Packages, Enable Unattended Security Updates & Set Timezone

Run on EC2:
```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades
sudo timedatectl set-timezone Asia/Kolkata
timedatectl
```

**Explanation of every command:**
- `sudo apt update`: Refreshes the local package index from Ubuntu mirrors so you fetch the latest security-patched versions.
- `sudo apt upgrade -y`: Installs pending OS security and bugfix updates (`-y` answers yes non-interactively).
- `unattended-upgrades`: Automatically applies Ubuntu security patches (OpenSSL, kernel, Nginx, OpenJDK CVEs) without waiting for manual maintenance.
- `sudo timedatectl set-timezone Asia/Kolkata`:
  - **Why?** Although `AlertDigestScheduler.java` already hardcodes `zone = "Asia/Kolkata"` for the 8:00 AM IST daily alert digest (`@Scheduled(cron = "...", zone = "Asia/Kolkata")`), EC2 defaults to `UTC`. Setting OS timezone to IST makes `journalctl`, Nginx access/error logs, and backup filenames align directly with Indian business hours and GST invoice dates.

---

### 7.3 Add a 2 GB Swap File (OOM Safety Net)

Run on EC2:
```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-yfc-swap.conf
sudo sysctl -p /etc/sysctl.d/99-yfc-swap.conf
free -h
```

**Explanation of every command & why:**
- `fallocate -l 2G /swapfile`: Instantly allocates a contiguous 2 GB file on disk.
- `chmod 600 /swapfile`: Only `root` can read/write swap (swap can hold decrypted memory pages; world-readable swap is a security bug).
- `mkswap /swapfile` & `swapon /swapfile`: Formats the file as Linux swap space and activates it immediately.
- `/etc/fstab` line: Persists swap across EC2 reboots.
- `vm.swappiness=10`: Tells Linux to strongly prefer keeping Java in fast RAM (default is `60`) and touch swap *only* under real memory pressure.
- **Why add swap when we already chose 2 GB RAM?** During occasional spikes (e.g., simultaneous report generation + large image validation + `apt` update), swap prevents an instant Linux OOM kill. It is a **safety airbag**, not a substitute for RAM.

---

### 7.4 Install Runtime Packages Only (Java 21 JRE, Nginx, Certbot, `psql`)

Run on EC2:
```bash
sudo apt install -y openjdk-21-jre-headless nginx certbot python3-certbot-nginx postgresql-client
java -version
nginx -v
psql --version
```

**Explanation of every package & why NOT others:**
- `openjdk-21-jre-headless`:
  - **Why `jre-headless` instead of `openjdk-21-jdk`?** Production only *runs* the compiled JAR—it never runs `javac` or GUI (`Swing`/`AWT` X11) libraries. `jre-headless` installs ~200 MB less software and shrinks the security surface. *(Note: `javax.imageio.ImageIO` used in `DocumentService.java` works cleanly in headless mode.)*
- `nginx`: High-performance reverse proxy + static file server for `frontend/dist`.
- `certbot` + `python3-certbot-nginx`: Official Let's Encrypt client + Nginx plugin that obtains free TLS certificates, configures modern TLS ciphers in Nginx, and installs a `systemd` auto-renewal timer.
- `postgresql-client`: Gives you `psql`, `pg_dump`, and `pg_restore` on EC2 for connectivity verification, diagnostics, and manual backups—it does **NOT** install a PostgreSQL server.
- **Why NOT install `maven` or `nodejs` on EC2?** We build artifacts cleanly on your local machine (or CI) and upload only compiled output.

---

### 7.5 Create Dedicated Service User & Directory Layout

Run on EC2:
```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin yfc
sudo mkdir -p /opt/yfc/releases /opt/yfc/certs /etc/yfc /var/www/yfc
sudo chown -R yfc:yfc /opt/yfc
sudo chmod 750 /opt/yfc /opt/yfc/releases /opt/yfc/certs
sudo chown -R ubuntu:www-data /var/www/yfc
sudo chmod 755 /var/www/yfc
```

**Explanation of every command & flag:**
- `useradd --system --no-create-home --shell /usr/sbin/nologin yfc`:
  - `--system`: Creates a low-UID system account (not an interactive human user).
  - `--no-create-home`: Does not create `/home/yfc`.
  - `--shell /usr/sbin/nologin`: Even if someone tried `su yfc` or SSH as `yfc`, interactive shell login is blocked.
  - **Why NOT run Spring Boot as `root` or `ubuntu`?** Principle of Least Privilege. If an attacker ever exploited an application bug, running as `yfc` prevents them from modifying Nginx configs, SSH keys, `/etc/yfc/yfc.env`, or system binaries.
- Directory layout:
  - `/opt/yfc/releases/`: Stores versioned JARs (`yfc-backend-YYYYMMDD-HHMMSS.jar`) so you can roll back instantaneously.
  - `/opt/yfc/current.jar`: Symlink pointing to the active JAR in `releases/`.
  - `/opt/yfc/certs/`: Holds the AWS RDS CA bundle.
  - `/etc/yfc/`: Holds the root-only `yfc.env` secrets file.
  - `/var/www/yfc/`: Holds the static built Vite frontend (`index.html`, `assets/`, `media/`). Owned by `ubuntu:www-data` so `ubuntu` can upload via `rsync` and Nginx (`www-data`) can read it cleanly without write permissions!

---

### 7.6 Install the AWS RDS Global CA Bundle

Run on EC2:
```bash
sudo curl -fsSL https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem \
  -o /opt/yfc/certs/aws-rds-global-bundle.pem
sudo chown root:yfc /opt/yfc/certs/aws-rds-global-bundle.pem
sudo chmod 640 /opt/yfc/certs/aws-rds-global-bundle.pem
```

**Explanation of flags & why:**
- `curl -fsSL`:
  - `-f`: Fail non-zero on HTTP 4xx/5xx instead of silently writing an error HTML page into the `.pem` file!
  - `-sS`: Silent progress bar, but still print errors if something fails.
  - `-L`: Follow HTTPS redirects.
- **Why do we need this CA file?** AWS RDS PostgreSQL presents TLS certificates signed by Amazon RDS's own Certificate Authority (`rds-ca-rsa2048-g1` / `rds-ca-ecc384-g1`), which is **not** in Ubuntu's or Java's default OS truststore.
- **Why `sslmode=verify-full` instead of `sslmode=require` or `sslmode=disable`?**
  - `disable`: Sends your DB password and accounting data in plaintext. Never acceptable.
  - `require`: Encrypts the tunnel, **but does not verify the server certificate or hostname** (vulnerable to active MITM attacks).
  - `verify-full` + `sslrootcert=...`: Encrypts traffic, verifies the certificate chains to AWS's official RDS CA, **and** verifies the certificate hostname matches your RDS endpoint.

---

### 7.7 Test RDS Connectivity from EC2 Right Now (Before Deploying App)

Before configuring Spring Boot, prove that VPC routing, Security Groups, TLS CA verification, credentials, and the `yfc` database all work:

```bash
psql "host=<your-rds-endpoint> port=5432 dbname=yfc user=yfc sslmode=verify-full sslrootcert=/opt/yfc/certs/aws-rds-global-bundle.pem" \
  -c "SELECT version(), current_database(), current_user;"
```
*(Enter your RDS master password when prompted.)*

**Expected output:**
```text
                                                 version                                                  | current_database | current_user
----------------------------------------------------------------------------------------------------------+------------------+--------------
 PostgreSQL 17.x on aarch64-unknown-linux-gnu, compiled by ...                                            | yfc              | yfc
```

**Why run this test now?**
- If it **hangs / times out**: Check `yfc-rds-sg` inbound rule (port `5432` must allow `yfc-ec2-sg`), and confirm EC2 and RDS are in the **same VPC**.
- If it says **`database "yfc" does not exist`**: You left *Initial database name* blank in RDS. Connect to `dbname=postgres` and run `CREATE DATABASE yfc;` once.
- If it says **`certificate verify failed`**: Re-download `/opt/yfc/certs/aws-rds-global-bundle.pem` and verify you used the exact RDS endpoint hostname (not an IP address).

---

### 7.8 Create the Production Environment File (`/etc/yfc/yfc.env`)

Create the file with `sudo nano /etc/yfc/yfc.env`:

```dotenv
# ============================================================
# YFC Transport ERP — Production Runtime Configuration
# Read by systemd (root) before dropping privileges to user yfc
# ============================================================

# 1. Spring profile & network binding
SPRING_PROFILES_ACTIVE=prod
SERVER_ADDRESS=127.0.0.1
PORT=8080

# 2. New AWS RDS PostgreSQL 17 database (Password Auth + verified TLS)
DB_AUTH_MODE=password
DB_URL=jdbc:postgresql://<your-rds-endpoint>:5432/yfc?sslmode=verify-full&sslrootcert=/opt/yfc/certs/aws-rds-global-bundle.pem
DB_USER=yfc
DB_PASSWORD=<your-rds-master-password>
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10

# 3. Proxy awareness, rate limiting & persistent sessions
RATE_LIMIT_ENABLED=true
RATE_LIMIT_TRUST_PROXY=true
SESSION_TTL_HOURS=12

# 4. Private document storage on Amazon S3 (uses EC2 IAM Role — no keys!)
DOCUMENTS_STORAGE=S3
DOCUMENTS_S3_BUCKET=yfc-prod-documents-<your-account-id>
DOCUMENTS_S3_REGION=<your-aws-region>
DOCUMENTS_S3_PREFIX=yfc-documents

# 5. Scheduled daily operational alert digest (runs 08:00 IST)
ALERTS_SCHEDULING_ENABLED=true
ALERTS_DIGEST_CRON=0 0 8 * * *
# Optional: set an HTTPS webhook URL (Slack/Teams/custom) if you want external digest pushes
# ALERTS_WEBHOOK_URL=https://hooks.example.com/...

# 6. First-boot initial SUPER_ADMIN bootstrap
# Password MUST be 12 to 72 UTF-8 bytes (AccountService.validatePassword).
# IMPORTANT: Delete YFC_ADMIN_PASSWORD after first successful boot & login!
YFC_ADMIN_USERNAME=admin
YFC_ADMIN_PASSWORD=<replace-with-a-strong-16plus-char-password>
```

Lock down file permissions immediately:
```bash
sudo chown root:root /etc/yfc/yfc.env
sudo chmod 600 /etc/yfc/yfc.env
```

**Critical explanations for specific variables:**
- **`SERVER_ADDRESS=127.0.0.1`**: `application-prod.yml` does not set `server.address` (only `application-local.yml` does). Without `SERVER_ADDRESS=127.0.0.1`, Spring Boot binds `0.0.0.0:8080`. Spring Boot relaxed binding automatically maps `SERVER_ADDRESS` → `server.address`, locking Tomcat strictly to loopback.
- **`SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10`** vs **`DB_POOL_MAX_SIZE`**:
  - Look closely at `IamDatabaseConfiguration.java` line 25: `DB_POOL_MAX_SIZE` is only read when `DB_AUTH_MODE=iam`.
  - In `DB_AUTH_MODE=password`, Spring Boot auto-configures HikariCP directly, so `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` is the variable that controls pool size (default is already `10`, which fits comfortably inside `db.t4g.micro`'s ~80–110 connection limit).
- **`RATE_LIMIT_TRUST_PROXY=true`**:
  - Defaults to `false` in `application.yml`. Behind Nginx, if left `false`, every user appears to come from `127.0.0.1`, so 25 logins/min across *all* users combined would lock out the entire company! Setting `true` (paired with Nginx's `X-Forwarded-For $remote_addr`) ensures per-client-IP rate limiting works accurately and cannot be spoofed.
- **Why `chown root:root` and `chmod 600`?**
  - `systemd` parses `EnvironmentFile=` as `root` *before* switching the child process to `User=yfc`. Making the file `root:root` `0600` means neither `ubuntu` nor even a compromised `yfc` shell could read `/etc/yfc/yfc.env` from disk.
- **Warning on `systemd` `EnvironmentFile=` syntax:** Do **not** put `export ` in front of lines and avoid wrapping values in quotes unless the value contains spaces—`systemd` treats quotes differently than `bash` in some edge cases. Keep values clean and unquoted.

---

### 7.9 Create the `systemd` Service (`/etc/systemd/system/yfc-backend.service`)

Create the unit file with `sudo nano /etc/systemd/system/yfc-backend.service`:

```ini
[Unit]
Description=YFC Transport ERP Backend (Spring Boot)
Documentation=file:///opt/yfc
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=yfc
Group=yfc
WorkingDirectory=/opt/yfc
EnvironmentFile=/etc/yfc/yfc.env

ExecStart=/usr/bin/java \
  -Xms256m \
  -Xmx1024m \
  -XX:+UseG1GC \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.io.tmpdir=/tmp \
  -jar /opt/yfc/current.jar

# Restart automatically on crash, but stop flapping if misconfigured
Restart=on-failure
RestartSec=5
StartLimitIntervalSec=120
StartLimitBurst=5

# Matches Spring Boot's server.shutdown=graceful + timeout-per-shutdown-phase=20s
KillSignal=SIGTERM
TimeoutStopSec=30

# Process & filesystem hardening
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadOnlyPaths=/opt/yfc

# Standard output/error go straight to journald
StandardOutput=journal
StandardError=journal
SyslogIdentifier=yfc-backend

[Install]
WantedBy=multi-user.target
```

Reload `systemd` and enable auto-start on boot (do **not** start it yet—we haven't uploaded the JAR):
```bash
sudo systemctl daemon-reload
sudo systemctl enable yfc-backend
```

**Line-by-line explanation of every directive:**
- `After=network-online.target` & `Wants=network-online.target`: Ensures networking and DNS resolution are actually ready after an EC2 reboot before Spring Boot tries to connect to RDS.
- `User=yfc` / `Group=yfc`: Runs the JVM as our unprivileged system user.
- `-Xms256m -Xmx1024m`:
  - Sets initial heap to 256 MB and hard-caps maximum heap at **1024 MB** (50% of our 2 GB EC2 RAM).
  - **Why cap `-Xmx1024m` instead of leaving JVM defaults?** The JVM consumes memory *outside* the heap (Metaspace ~100–150 MB, CodeCache, thread stacks, Direct/Netty/NIO buffers ~150–250 MB). Capping heap at 1024 MB keeps total JVM RSS around ~1.3 GB max, leaving ~700 MB for Ubuntu OS, Nginx, and page cache so the server stays fast and never OOM-crashes. *(If you later upgrade EC2 to a 4 GB `t4g.medium`, raise this to `-Xmx2048m`.)*
- `-XX:+UseG1GC`: Modern low-pause garbage collector (standard for Java 21 server apps).
- `-XX:+ExitOnOutOfMemoryError`: **Critical!** Without this flag, a JVM that runs out of heap can stay alive as a "zombie" process returning 500 errors forever. Exiting immediately lets `systemd` detect the failure and cleanly restart it in 5 seconds.
- `KillSignal=SIGTERM` + `TimeoutStopSec=30`:
  - `application.yml` configures `server.shutdown: graceful` and `spring.lifecycle.timeout-per-shutdown-phase: 20s`.
  - When you run `systemctl restart yfc-backend`, `systemd` sends `SIGTERM`; Spring Boot stops accepting new HTTP requests, finishes in-flight database transactions cleanly (up to 20s), closes HikariCP connections, and exits—well inside `TimeoutStopSec=30` before `systemd` would ever resort to `SIGKILL`.
- `NoNewPrivileges=true`, `PrivateTmp=true`, `ProtectSystem=strict`, `ProtectHome=true`, `ReadOnlyPaths=/opt/yfc`:
  - Kernel-level sandboxing via `systemd`: the Java process has its own isolated `/tmp`, cannot escalate privileges, cannot view `/home/ubuntu`, and **cannot write anywhere on the filesystem** (not even `/opt/yfc`), because all persistent data goes to RDS PostgreSQL and S3! *(Note: if you ever temporarily switched `DOCUMENTS_STORAGE=LOCAL`, you would need to add `ReadWritePaths=/opt/yfc/data`.)*

---

### 7.10 Configure Nginx Reverse Proxy & SPA Static Serving

Create `/etc/nginx/sites-available/yfc`:
```bash
sudo nano /etc/nginx/sites-available/yfc
```

Paste the following configuration (replace `erp.yourdomain.com` with your real domain):

```nginx
server {
    listen 80;
    listen [::]:80;
    server_name erp.yourdomain.com;

    # Hide Nginx version number from Server response headers and error pages
    server_tokens off;

    # Spring Boot allows 2MB per file / 3MB total multipart request (application.yml).
    # Setting 4m here ensures valid 2MB PDFs/images with multipart boundary headers
    # pass through Nginx and let Spring return its clean JSON validation errors.
    client_max_body_size 4m;

    # Gzip compression for text assets (JS, CSS, JSON, SVG)
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_types
        text/plain
        text/css
        text/javascript
        application/javascript
        application/json
        application/xml
        image/svg+xml;

    # ------------------------------------------------------------
    # 1. Backend proxy: /api/*, /login, /logout
    #    Notice /actuator/* is intentionally NOT proxied publicly!
    # ------------------------------------------------------------
    location ~ ^/(api/|login$|logout$) {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;

        # SECURITY CRITICAL:
        # Use $remote_addr (NOT $proxy_add_x_forwarded_for) when Nginx is directly
        # internet-facing! PublicRateLimitFilter.java reads the FIRST IP in
        # X-Forwarded-For. Using $proxy_add_x_forwarded_for would let an attacker
        # send a fake "X-Forwarded-For: 1.2.3.4" header to bypass login rate limits.
        # Setting $remote_addr overwrites any client-supplied header with the true TCP IP.
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Port $server_port;

        proxy_connect_timeout 10s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
        # Note: Spring Security and DocumentController already emit Cache-Control: no-store
        # and security headers on API/document responses; omit add_header here to avoid duplicates.
    }

    # ------------------------------------------------------------
    # 2. Vite hashed bundles (/assets/*) — safe to cache for 1 year
    # ------------------------------------------------------------
    location /assets/ {
        root /var/www/yfc;
        add_header Cache-Control "public, max-age=31536000, immutable";
        add_header X-Content-Type-Options "nosniff" always;
        try_files $uri =404;
    }

    # ------------------------------------------------------------
    # 3. Public media images (/media/*) — unhashed filenames, 7-day cache
    # ------------------------------------------------------------
    location /media/ {
        root /var/www/yfc;
        add_header Cache-Control "public, max-age=604800";
        add_header X-Content-Type-Options "nosniff" always;
        try_files $uri =404;
    }

    # ------------------------------------------------------------
    # 4. React SPA routes (/, /staff, /signin, /signup, /portal, /orders, etc.)
    #    Fall back to /index.html so BrowserRouter handles client-side URLs.
    #    Never cache index.html so users immediately get new releases.
    # ------------------------------------------------------------
    location / {
        root /var/www/yfc;
        try_files $uri /index.html;
        add_header Cache-Control "no-cache" always;
        add_header X-Frame-Options "DENY" always;
        add_header X-Content-Type-Options "nosniff" always;
        add_header Referrer-Policy "strict-origin-when-cross-origin" always;
        add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;
    }
}
```

Enable the site, remove Ubuntu's default site, test syntax, and reload Nginx:
```bash
sudo rm -f /etc/nginx/sites-enabled/default
sudo ln -sf /etc/nginx/sites-available/yfc /etc/nginx/sites-enabled/yfc
sudo nginx -t
sudo systemctl reload nginx
```

**Why each Nginx detail matters specifically for YFC:**
1. **Regex `location ~ ^/(api/|login$|logout$)`**:
   - Every Spring controller in YFC lives under `/api/v1/...`, and Spring Security form login/logout live at exact paths `/login` and `/logout`.
   - Frontend React Router uses `/`, `/staff`, `/signin`, `/signup`, `/join`, `/portal`, `/reset`, `/dashboard`, `/sales`, `/orders`, etc.—zero path collision!
   - **`/actuator/health` is intentionally omitted** from Nginx so external scanners cannot probe management endpoints; you check health locally over `http://127.0.0.1:8080/actuator/health` from EC2.
2. **`X-Forwarded-For $remote_addr` + `X-Forwarded-Proto $scheme`**:
   - With `server.forward-headers-strategy: native` in `application-prod.yml`, Tomcat enables `RemoteIpValve`, which trusts `127.0.0.1` by default, sees `X-Forwarded-Proto: https`, marks `request.isSecure() = true` (making `YFC_SESSION` and `JSESSIONID` `Secure` cookies work), and sets `request.getRemoteAddr()` to the true visitor IP.
   - Using `$remote_addr` instead of `$proxy_add_x_forwarded_for` prevents attackers from forging `X-Forwarded-For` headers to bypass `PublicRateLimitFilter.java` (which reads `forwarded.split(",")[0]`).
3. **Why `try_files $uri =404;` on `/assets/` and `/media/`, vs `try_files $uri /index.html;` on `/`?**
   - If a stale browser requests a missing `.js` chunk, returning `404` fails cleanly instead of serving `index.html` with MIME type `text/html` (which triggers confusing `Failed to load module script` console errors).
   - For navigation URLs like `/orders/1234` or `/staff`, `try_files $uri /index.html;` returns `index.html` so React Router (`BrowserRouter` in `frontend/src/main.tsx`) renders the right view on page refresh.
4. **Why `Cache-Control: public, max-age=604800` on `/media/` instead of `immutable`?**
   - Files in `frontend/public/media/` (`hero.jpg`, `truck.jpg`, etc.) do **not** have content hashes in their filenames, unlike files in `frontend/dist/assets/` (`index-B4xK9a12.js`). Marking unhashed images `immutable` for 1 year would prevent users from ever seeing updated photos!
5. **Why `nginx -t` before `systemctl reload nginx`?**
   - `nginx -t` validates config syntax without touching the live server; `reload` applies changes gracefully with zero dropped connections.

---

### 7.11 Obtain Free HTTPS TLS Certificate with Certbot

Ensure `dig +short erp.yourdomain.com A` already returns your EC2 Elastic IP, then run on EC2:

```bash
sudo certbot --nginx -d erp.yourdomain.com --non-interactive --agree-tos -m you@yourdomain.com --redirect
```

**Explanation of every flag:**
- `--nginx`: Uses the Nginx plugin to automatically serve the temporary Let's Encrypt validation token on port `80`, prove you own `erp.yourdomain.com`, obtain the certificate, and edit `/etc/nginx/sites-available/yfc` to enable `listen 443 ssl`.
- `-d erp.yourdomain.com`: The domain name for the TLS certificate.
- `--agree-tos -m you@yourdomain.com`: Accepts Let's Encrypt terms and registers your email for urgent expiration warnings.
- `--redirect`: Automatically adds a `301` redirect block from `http://` (port `80`) to `https://` (port `443`).

Verify automatic renewal works (Let's Encrypt certificates expire every 90 days; Certbot renews automatically at ~60 days):
```bash
sudo systemctl status certbot.timer --no-pager
sudo certbot renew --dry-run
```
Expected last line: `Congratulations, all simulated renewals succeeded`.

---

### 7.12 Cap `journald` Disk Usage (Prevent Logs Filling the Disk)

Run on EC2:
```bash
sudo mkdir -p /etc/systemd/journald.conf.d
cat <<'EOF' | sudo tee /etc/systemd/journald.conf.d/yfc-limits.conf
[Journal]
SystemMaxUse=500M
MaxRetentionSec=30day
EOF
sudo systemctl restart systemd-journald
```
- **Why?** Without a cap, `journald` can consume up to 10% of the disk over months. Capping at `500M` / `30day` guarantees logs never exhaust disk space while preserving 30 days of backend logs.

---

## 8. Phase 6 — Building the Backend JAR & Frontend Bundle Locally

All commands in this phase run on **your local workstation** inside `/Users/purplematrix/Desktop/YFC`.

### 8.1 Step 1 — Run Tests First (Recommended Before Every Release)

Never deploy untested code to a production financial ERP.

**Backend tests (H2 in-memory + Spring Modulith architecture + unit & journey tests):**
```bash
cd /Users/purplematrix/Desktop/YFC/backend
mvn -B verify
```
- **`-B` (Batch mode):** Disables noisy interactive ANSI progress bars and ensures clean log output.
- **`verify`:** Compiles code, runs all unit + Spring Boot integration + Modulith verification tests, and packages the JAR.
- **Why does `mvn verify` run safely without AWS credentials?** Without `backend/.env.aws` loaded, Spring Boot's `test` profile uses in-memory H2 (`backend/src/test/resources/application-test.yml`), never touching your production RDS database.

**Frontend tests:**
```bash
cd /Users/purplematrix/Desktop/YFC/frontend
npm ci
npm test
```
- **Why `npm ci` instead of `npm install`?**
  - `npm ci` ("clean install") installs the **exact** dependency versions pinned in `frontend/package-lock.json` and deletes `node_modules` first.
  - `npm install` can silently drift patch/minor versions or modify `package-lock.json`, causing "works on my machine, breaks in prod" builds.

### 8.2 Step 2 — Build the Production Backend Fat JAR

```bash
cd /Users/purplematrix/Desktop/YFC/backend
mvn -B clean package -DskipTests
ls -lh target/yfc-backend-*.jar
```

**Explanation of every piece:**
- `clean`: Deletes the old `target/` directory first so stale compiled `.class` files or deleted migration files from previous branches cannot accidentally stay in the JAR.
- `package`: Compiles Java 21 bytecode, copies `src/main/resources` (including `application.yml`, `application-prod.yml`, and all 26 Flyway SQL files in `db/migration/`), and triggers `spring-boot-maven-plugin` (`repackage`) to assemble a self-contained executable fat JAR containing embedded Tomcat + all dependencies.
- `-DskipTests`: Skips re-running the test suite we *just* passed in Step 8.1 (if you didn't run Step 8.1, omit `-DskipTests` so Maven always tests before packaging!).
- **Output file:** `backend/target/yfc-backend-0.1.0-SNAPSHOT.jar` (~84 MB). *(Ignore `yfc-backend-0.1.0-SNAPSHOT.jar.original`—that is the thin pre-repackage JAR without dependencies and is not runnable.)*
- **Cross-architecture compatibility:** Because Java compiles to platform-independent JVM bytecode, a JAR built on an Apple Silicon Mac (ARM64) or Intel Mac (x86_64) runs identically on both `t4g.small` (ARM64) and `t3.small` (x86_64) EC2 instances!

### 8.3 Step 3 — Build the Production Frontend Static Bundle

```bash
cd /Users/purplematrix/Desktop/YFC/frontend
npm run build
ls -la dist/ dist/assets/ dist/media/
```

**What `npm run build` actually executes (from `frontend/package.json`):**
1. `tsc -b`: Runs strict TypeScript typechecking across the entire frontend; aborts the build if there is any type error.
2. `vite build`: Bundles, tree-shakes, and minifies React 19 + TanStack Query + React Router + CSS into content-hashed chunks (`vendor-react`, `vendor-router`, `vendor-query`, `vendor-icons`, plus lazy-loaded route chunks configured in `frontend/vite.config.ts`) inside `frontend/dist/`, and copies static files from `frontend/public/media/` to `frontend/dist/media/`.
- **Why no environment variables are needed during `vite build`?** Because `frontend/src/api.ts` uses relative paths (`/api/v1/...`, `/login`, `/logout`), the compiled bundle has **zero hardcoded domain or server URLs**—it automatically talks to whatever HTTPS origin serves it.

---

## 9. Phase 7 — Deploying Artifacts to EC2 & Fast-Rollback Workflow

Run these commands from **your Mac** in `/Users/purplematrix/Desktop/YFC`.

### 9.1 Set Local Shell Variables (For Clean Copy-Paste)

```bash
EC2_HOST="ubuntu@erp.yourdomain.com"
SSH_KEY="$HOME/.ssh/yfc-prod-key.pem"
RELEASE_TAG="$(date +%Y%m%d-%H%M%S)"
```

### 9.2 Upload the Backend JAR to a Versioned Path

```bash
scp -i "$SSH_KEY" \
  backend/target/yfc-backend-0.1.0-SNAPSHOT.jar \
  "$EC2_HOST:/tmp/yfc-backend-${RELEASE_TAG}.jar"
```
- **Why upload to `/tmp/` first instead of directly to `/opt/yfc/releases/`?**
  - `/opt/yfc/releases/` is owned by `yfc:yfc` (`750`) for security, so the `ubuntu` SSH user cannot write into it directly. Uploading to `/tmp/` and moving with `sudo` preserves strict permissions AND guarantees the file finishes transferring completely before `systemd` ever sees it.

### 9.3 Sync the Built Frontend Bundle to `/var/www/yfc/`

```bash
rsync -avz --delete \
  -e "ssh -i $SSH_KEY" \
  frontend/dist/ \
  "$EC2_HOST:/var/www/yfc/"
```

**Explanation of every flag (important!):**
- `-a` (archive): Preserves directory structure, timestamps, and permissions recursively.
- `-v` (verbose): Lists every file uploaded or deleted so you can visually confirm the deploy.
- `-z` (compress): Compresses bytes over the SSH wire for faster uploads.
- `--delete`: Deletes old hashed JS/CSS chunks from `/var/www/yfc/assets/` that no longer exist in `frontend/dist/`, preventing unbounded clutter on EC2.
- **Trailing slash on `frontend/dist/`:** Crucial in `rsync`! `frontend/dist/` means *"copy the **contents** of `dist/` into `/var/www/yfc/`"* (so `/var/www/yfc/index.html` is created). Without the trailing slash, `rsync` would create `/var/www/yfc/dist/index.html` and break the site!

### 9.4 Atomically Activate the New Backend JAR & Restart `yfc-backend`

```bash
ssh -i "$SSH_KEY" "$EC2_HOST" "
  set -euo pipefail
  sudo mv /tmp/yfc-backend-${RELEASE_TAG}.jar /opt/yfc/releases/yfc-backend-${RELEASE_TAG}.jar
  sudo chown yfc:yfc /opt/yfc/releases/yfc-backend-${RELEASE_TAG}.jar
  sudo chmod 500 /opt/yfc/releases/yfc-backend-${RELEASE_TAG}.jar
  sudo ln -sfn /opt/yfc/releases/yfc-backend-${RELEASE_TAG}.jar /opt/yfc/current.jar
  sudo systemctl restart yfc-backend

  # Keep only the 5 most recent JAR releases on disk to save space
  # (run inside sudo bash -c so the wildcard glob expands as root inside 750 yfc:yfc dir)
  sudo bash -c 'ls -1t /opt/yfc/releases/yfc-backend-*.jar | tail -n +6 | xargs -r rm -f'
"
```

**Explanation of every command:**
- `set -euo pipefail`: Aborts immediately if any command fails so you never restart against a half-copied release.
- `chmod 500`: Read + execute for owner `yfc` only (not even `yfc` can modify its own JAR!).
- `ln -sfn /opt/yfc/releases/yfc-backend-${RELEASE_TAG}.jar /opt/yfc/current.jar`:
  - `-s`: Symbolic link.
  - `-f`: Replace existing symlink.
  - `-n`: Treat symlink to target cleanly without dereferencing.
  - **Why use a symlink instead of overwriting one `yfc-backend.jar` file in place?**
    1. Overwriting a JAR file while a running JVM still has it memory-mapped can crash the JVM with `ZipException` / `SIGBUS` *before* `systemctl restart` runs!
    2. Swapping a symlink is atomic and keeps the last 5 working JARs intact in `/opt/yfc/releases/`.
- `tail -n +6 | xargs -r sudo rm -f`: Automatically retains the 5 newest JARs (~425 MB total) and deletes older ones (`-r` means "do nothing if there are fewer than 6 files").

### 9.5 Fast 3-Second Rollback Procedure (If a Release Ever Fails)

If a newly deployed backend release fails to start or introduces a regression (assuming no destructive DB schema change):
```bash
# On EC2: list kept releases (newest first)
sudo ls -lt /opt/yfc/releases/

# Repoint symlink to the previous working JAR and restart
sudo ln -sfn /opt/yfc/releases/yfc-backend-<PREVIOUS_TAG>.jar /opt/yfc/current.jar
sudo systemctl restart yfc-backend
```
*(Note: Flyway migrations are forward-only; never downgrade to a JAR older than a newly applied incompatible schema migration without reviewing the SQL first.)*

---

## 10. Phase 8 — First Boot, Full Verification & Mandatory Security Cleanup

### 10.1 Watch Live First-Boot Startup Logs

SSH into EC2 and stream logs immediately:
```bash
sudo journalctl -u yfc-backend -f
```

**What you MUST see on a healthy first boot against the empty RDS database:**
1. Active profile logged: `The following 1 profile is active: "prod"`.
2. HikariCP connects to your RDS PostgreSQL 17 endpoint over TLS.
3. Flyway detects an empty database, creates `flyway_schema_history`, and applies all **26 migration scripts** (`V1` through `V17`, and `V19` through `V27` — note `V18` is intentionally absent from project history, so version progresses cleanly from `1` to `27`).
4. Hibernate runs `ddl-auto: validate` (`application.yml`) and confirms JPA entities match the PostgreSQL schema 100%.
5. `BootstrapAdmin` (`@Profile("!test | local")`, which is active under `prod`) sees `accounts.count() == 0`, creates your initial `SUPER_ADMIN` account, and writes `ADMIN_BOOTSTRAPPED` to `audit_events`.
6. Tomcat starts on `127.0.0.1:8080` and prints `Started YfcApplication in ... seconds`.

Press `Ctrl+C` to exit live log streaming.

---

### 10.2 Complete 8-Point Production Verification Checklist

Run every check below on EC2 (unless marked "from your Mac/browser"):

#### Check 1 — Application Health Endpoint (on EC2)
```bash
curl -sS http://127.0.0.1:8080/actuator/health
```
- **Expected:** `{"status":"UP"}` (details hidden by `show-details: never` in `application.yml`).

#### Check 2 — Confirm Port `8080` Is Bound ONLY to Loopback (on EC2)
```bash
sudo ss -tlnp | grep -E ':80|:443|:8080'
```
- **Expected:**
  - `nginx` listening on `0.0.0.0:80`, `[::]:80`, `0.0.0.0:443`, `[::]:443`.
  - `java` listening **ONLY** on `127.0.0.1:8080` (must **NOT** say `*:8080` or `0.0.0.0:8080`).

#### Check 3 — Confirm `/actuator/health` Is NOT Exposed Publicly (from your Mac)
```bash
curl -sS -o /dev/null -w "%{http_code}\n" https://erp.yourdomain.com/actuator/health
```
- **Expected:** Does **not** return Spring's `{"status":"UP"}` JSON (Nginx routes it to SPA `index.html`, keeping internal actuator endpoints private).

#### Check 4 — Confirm HTTP Automatically Redirects to HTTPS (from your Mac)
```bash
curl -I http://erp.yourdomain.com
```
- **Expected:** `HTTP/1.1 301 Moved Permanently` with `Location: https://erp.yourdomain.com/`.

#### Check 5 — Confirm Public API & CSRF Token Work End-to-End Through Nginx (from your Mac)
```bash
curl -sS https://erp.yourdomain.com/api/v1/csrf
echo ""
curl -sS https://erp.yourdomain.com/api/v1/site | head -c 300
echo ""
```
- **Expected:** Valid JSON containing `headerName`, `parameterName`, `token` from `/api/v1/csrf`, and public company site JSON from `/api/v1/site`.

#### Check 6 — Verify Browser Login, `Secure` Cookies & DB-Backed Sessions
1. Open `https://erp.yourdomain.com/` → verify public site and `/media/*.jpg` images load with a valid HTTPS padlock.
2. Open `https://erp.yourdomain.com/staff` → sign in with `YFC_ADMIN_USERNAME` and `YFC_ADMIN_PASSWORD`.
3. Open Browser DevTools → **Application → Cookies → `https://erp.yourdomain.com`**:
   - Confirm **`JSESSIONID`** and **`YFC_SESSION`** are both present with **`HttpOnly = ✓`**, **`Secure = ✓`**, and **`SameSite = Lax`**.
4. Test session persistence across backend restarts:
   - Run `sudo systemctl restart yfc-backend` on EC2, wait 10 seconds, then refresh the browser page.
   - **Expected:** You remain signed in without being kicked to the login page, proving `PersistentSessionFilter.java` + PostgreSQL `persistent_sessions` table work!

#### Check 7 — Verify S3 Document Upload & Download (IAM Role End-to-End)
1. In the Staff ERP UI, upload a small test PDF or JPG/PNG (under 2 MB) to a test record (or inspect after creating your first operational record).
2. Check PostgreSQL on EC2:
   ```bash
   psql "host=<your-rds-endpoint> port=5432 dbname=yfc user=yfc sslmode=verify-full sslrootcert=/opt/yfc/certs/aws-rds-global-bundle.pem" \
     -c "SELECT id, filename, storage_backend, storage_key, (content IS NULL) AS db_blob_empty FROM private_documents ORDER BY uploaded_at DESC LIMIT 5;"
   ```
   - **Expected:** `storage_backend = S3`, `storage_key = yfc-documents/...`, `db_blob_empty = t` (`true`).
3. Download the file back from the ERP UI and confirm it opens cleanly.
4. Check the AWS S3 Console (`yfc-prod-documents-...` → `yfc-documents/`): the uploaded object is present and private.

#### Check 8 — Verify Database Schema History in RDS (on EC2)
```bash
psql "host=<your-rds-endpoint> port=5432 dbname=yfc user=yfc sslmode=verify-full sslrootcert=/opt/yfc/certs/aws-rds-global-bundle.pem" \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```
- **Expected:** Top row shows version `27` (`phase2 enterprise ops`) with `success = t`.

---

### 10.3 MANDATORY Post-Bootstrap Security Cleanup

Once you have signed in as your initial `SUPER_ADMIN` (and ideally created named personal admin accounts or rotated the initial password):

1. Edit `/etc/yfc/yfc.env` on EC2:
   ```bash
   sudo nano /etc/yfc/yfc.env
   ```
2. **Delete the `YFC_ADMIN_PASSWORD=...` line completely** (and `YFC_ADMIN_USERNAME` if desired).
3. Restart the backend to confirm it boots cleanly without bootstrap credentials:
   ```bash
   sudo systemctl restart yfc-backend
   sudo systemctl status yfc-backend --no-pager
   ```

**Why is this safe AND necessary?**
- In `AccountService.java` line 200: `if (accounts.count() != 0) return;`. Once at least one user exists in the `accounts` table, `BootstrapAdmin` does nothing at startup—so removing `YFC_ADMIN_PASSWORD` will never break restarts.
- Keeping a plaintext super-admin password sitting inside `/etc/yfc/yfc.env` after initial setup serves no purpose and increases exposure if the env file is ever backed up or misread. All future password changes use the in-app **People → Issue Password Reset** flow (`/reset`).

---

## 11. Phase 9 — Day-2 Operations, Backups, Data Migration & Troubleshooting

### 11.1 Essential Operations Commands (On EC2)

| Task | Command |
|---|---|
| Service status | `sudo systemctl status yfc-backend` |
| Live backend logs | `sudo journalctl -u yfc-backend -f` |
| Last 200 log lines | `sudo journalctl -u yfc-backend -n 200 --no-pager` |
| Logs since today | `sudo journalctl -u yfc-backend --since today` |
| Only errors/warnings | `sudo journalctl -u yfc-backend -p warning --since "24 hours ago"` |
| Nginx access & error logs | `sudo tail -f /var/log/nginx/access.log /var/log/nginx/error.log` |
| Check RAM & swap usage | `free -h` |
| Check disk space | `df -h` |
| Check Java CPU/memory | `ps -o pid,user,%cpu,%mem,rss,cmd -C java` |
| Restart backend cleanly | `sudo systemctl restart yfc-backend` |
| Reload Nginx cleanly | `sudo nginx -t && sudo systemctl reload nginx` |

### 11.2 Backups, Safety & Billing Alerts

1. **Before any major release or schema migration:**
   - Go to **RDS → `yfc-prod-db` → Actions → Take snapshot** (name e.g. `yfc-before-v28-2026-11-23`). Takes ~1 minute and lets you restore the entire database to that exact state.
2. **Logical dump backup (portable SQL archive):**
   ```bash
   pg_dump "host=<your-rds-endpoint> port=5432 dbname=yfc user=yfc sslmode=verify-full sslrootcert=/opt/yfc/certs/aws-rds-global-bundle.pem" \
     --format=custom --no-owner --no-privileges \
     -f "/tmp/yfc-backup-$(date +%Y%m%d-%H%M).dump"
   ```
   - `--format=custom`: Compressed binary archive compatible with `pg_restore` (supports parallel restore and selective table restore).
   - `--no-owner --no-privileges`: Prevents role-name conflicts when restoring into another PostgreSQL instance or user.
3. **Optional — If you ever want to copy data from an older database into this new RDS DB:**
   - Stop `yfc-backend` on EC2 first (`sudo systemctl stop yfc-backend`) so Flyway doesn't run concurrently.
   - Dump the source database with `pg_dump --format=custom --no-owner --no-privileges -f source.dump`.
   - Restore into the empty new RDS `yfc` database from EC2:
     ```bash
     pg_restore "host=<your-rds-endpoint> port=5432 dbname=yfc user=yfc sslmode=verify-full sslrootcert=/opt/yfc/certs/aws-rds-global-bundle.pem" \
       --no-owner --no-privileges --clean --if-exists \
       source.dump
     ```
   - Start `yfc-backend` (`sudo systemctl start yfc-backend`). Flyway will automatically apply any newer migrations on top of the restored schema!
   - Delete `source.dump` from `/tmp/` once verified.
4. **AWS Budgets Alert (Highly Recommended):**
   - Go to **AWS Billing and Cost Management → Budgets → Create a budget → Monthly cost budget** (e.g., \$50 or \$75) with email alerts at 80% and 100%. Takes 2 minutes and prevents surprise charges.

### 11.3 Comprehensive Troubleshooting Guide

| Symptom | Root Cause | Exact Diagnosis & Fix |
|---|---|---|
| Backend hangs at startup then fails with `Connection to ...:5432 refused` or `timed out` | Security Group or VPC mismatch between EC2 and RDS | Confirm both EC2 and RDS are in `yfc-prod-vpc`, and `yfc-rds-sg` has an Inbound rule for TCP `5432` from `yfc-ec2-sg`. Test with the `psql` command from Section 7.7. |
| `PSQLException: FATAL: database "yfc" does not exist` | Initial database name was left blank when creating RDS | Run `psql "host=<rds> port=5432 dbname=postgres user=yfc sslmode=verify-full sslrootcert=/opt/yfc/certs/aws-rds-global-bundle.pem" -c "CREATE DATABASE yfc;"` and restart `yfc-backend`. |
| `SSL error` / `PKIX path building failed` / `Could not open SSL root certificate file` | Wrong path or unreadable permissions on `/opt/yfc/certs/aws-rds-global-bundle.pem` | Verify `ls -la /opt/yfc/certs/aws-rds-global-bundle.pem` is readable by group `yfc` (`640 root:yfc` or `644`) and exact path matches `sslrootcert=` in `/etc/yfc/yfc.env`. |
| Startup fails with `Password must have at least 12 characters...` | `YFC_ADMIN_PASSWORD` on an empty DB is shorter than 12 chars or > 72 UTF-8 bytes (`AccountService.java`) | Update `YFC_ADMIN_PASSWORD` in `/etc/yfc/yfc.env` to a 16+ char password and restart `yfc-backend`. |
| Login returns `204` or `403`, then immediately acts logged out | Accessing over plain `http://` instead of `https://`, OR Nginx missing `X-Forwarded-Proto $scheme` | Under `prod`, cookies have `Secure=true`. Ensure Certbot HTTPS is active, visit `https://`, and verify `proxy_set_header X-Forwarded-Proto $scheme;` is in `/etc/nginx/sites-available/yfc`. |
| Browser shows `502 Bad Gateway` | `yfc-backend` is stopped, still booting (takes ~5–10s), or crashed | Check `sudo systemctl status yfc-backend` and `sudo journalctl -u yfc-backend -n 100`. Also verify `PORT=8080` and `SERVER_ADDRESS=127.0.0.1` match `proxy_pass http://127.0.0.1:8080`. |
| Document upload fails with `413 Request Entity Too Large` | Nginx default `client_max_body_size` is `1m`, rejecting 1–2 MB uploads | Ensure `client_max_body_size 4m;` is set in `/etc/nginx/sites-available/yfc` and run `sudo nginx -t && sudo systemctl reload nginx`. |
| Document upload fails with `502 Bad Gateway` (`S3 upload failed with HTTP 403`) | EC2 IAM Role missing, wrong bucket name/region, or prefix mismatch | Check EC2 Console → Instance → Security → IAM Role is `yfc-ec2-role`. Verify policy Resource `arn:aws:s3:::<bucket>/yfc-documents/*` exactly matches `DOCUMENTS_S3_BUCKET` and `DOCUMENTS_S3_PREFIX` in `/etc/yfc/yfc.env`. |
| Legitimate users hit `429 Too Many Requests` quickly | `RATE_LIMIT_TRUST_PROXY` is `false` (all users share `127.0.0.1` bucket) | Set `RATE_LIMIT_TRUST_PROXY=true` in `/etc/yfc/yfc.env`, confirm Nginx sets `proxy_set_header X-Forwarded-For $remote_addr;`, and restart `yfc-backend`. |
| Refreshing `/orders` or `/staff` gives Nginx `404 Not Found` | Missing SPA fallback in Nginx | Ensure `location /` has `try_files $uri /index.html;` and `/var/www/yfc/index.html` exists. |
| `java` killed with `OutOfMemoryError` or `status=137` | Heap cap too small or instance RAM exhausted | Check `sudo dmesg -T | grep -i oom`. Confirm 2 GB swap is active (`free -h`) and instance is at least `t4g.small`/`t3.small` (2 GB RAM) with `-Xmx1024m`. Upgrade to `t4g.medium` (4 GB) if workload grows. |

---

## 12. Appendix A — Complete Environment Variable Reference

Every environment variable recognized by YFC backend (`backend/src/main/resources/application.yml`, `application-prod.yml`, `BootstrapAdmin.java`, `IamDatabaseConfiguration.java`):

| Environment Variable | Default | Production Value (Option B) | Notes |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | *(none)* | `prod` | Enables `forward-headers-strategy: native`, `cookie.secure: true`, 30m Tomcat session timeout. |
| `SERVER_ADDRESS` | `0.0.0.0` | `127.0.0.1` | Spring relaxed binding for `server.address`; restricts Tomcat strictly to local loopback behind Nginx. |
| `PORT` | `8080` | `8080` | HTTP listen port for embedded Tomcat. |
| `DB_AUTH_MODE` | `password` | `password` *(or `iam`)* | `password` uses Spring Boot HikariCP + `DB_PASSWORD`; `iam` activates `IamDatabaseConfiguration` token generator. |
| `DB_URL` | `jdbc:postgresql://localhost:5432/yfc` | `jdbc:postgresql://<rds>:5432/yfc?sslmode=verify-full&sslrootcert=/opt/yfc/certs/aws-rds-global-bundle.pem` | Full JDBC URL with verified TLS. |
| `DB_USER` | `yfc` | `yfc` | PostgreSQL login role. |
| `DB_PASSWORD` | *(empty)* | `<rds-password>` | Required when `DB_AUTH_MODE=password`; omit when `DB_AUTH_MODE=iam`. |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | `10` | `10` | Controls HikariCP pool size in `DB_AUTH_MODE=password`. |
| `DB_POOL_MAX_SIZE` | `10` | `10` *(IAM mode only)* | Controls HikariCP pool size in `DB_AUTH_MODE=iam` (must be `1`–`20`). |
| `AWS_REGION` | *(empty)* | e.g. `ap-south-2` | Required when `DB_AUTH_MODE=iam`; also acts as fallback for `DOCUMENTS_S3_REGION`. |
| `SESSION_TTL_HOURS` | `12` | `12` | Rolling TTL for DB-backed `YFC_SESSION` rows (`persistent_sessions`). |
| `RATE_LIMIT_ENABLED` | `true` | `true` | Enables per-IP rate limiting on login, password reset, public writes, and public reads. |
| `RATE_LIMIT_TRUST_PROXY` | `false` | `true` | **Must be `true` behind Nginx** so real client IP is rate-limited instead of `127.0.0.1`. |
| `RATE_LIMIT_LOGIN_PER_MIN` | `25` | `25` | Max `POST /login` attempts per IP per minute (in addition to `LoginProtection` 5-fail / 15-min per-username lockout). |
| `RATE_LIMIT_AUTH_PER_MIN` | `10` | `10` | Max `POST /api/v1/auth/password-reset` per IP per minute. |
| `RATE_LIMIT_PUBLIC_WRITE_PER_MIN` | `15` | `15` | Max public enquiries / account applications per IP per minute. |
| `RATE_LIMIT_PUBLIC_READ_PER_MIN` | `120` | `120` | Max public site / quote reads per IP per minute. |
| `DOCUMENTS_STORAGE` | `DATABASE` | `S3` | Active storage backend for new uploads (`DATABASE`, `LOCAL`, or `S3`). |
| `DOCUMENTS_LOCAL_DIR` | `./data/documents` | *(unused in S3 mode)* | Only used when `DOCUMENTS_STORAGE=LOCAL`. |
| `DOCUMENTS_S3_BUCKET` | *(empty)* | `yfc-prod-documents-<acct>` | Target S3 bucket name when `DOCUMENTS_STORAGE=S3`. |
| `DOCUMENTS_S3_REGION` | `${AWS_REGION:ap-south-2}` | e.g. `ap-south-2` | Region where your S3 bucket lives. |
| `DOCUMENTS_S3_PREFIX` | `yfc-documents` | `yfc-documents` | Key prefix inside S3 bucket; must match IAM policy Resource path. |
| `DOCUMENTS_S3_ENDPOINT` | *(empty)* | *(leave empty for AWS S3)* | Only for non-AWS S3-compatible stores (Cloudflare R2, MinIO). |
| `ALERTS_SCHEDULING_ENABLED` | `true` | `true` | Enables scheduled daily operational alert digest (`AlertDigestScheduler.java`). |
| `ALERTS_DIGEST_CRON` | `0 0 8 * * *` | `0 0 8 * * *` | Spring 6-field cron expression evaluated in `Asia/Kolkata` timezone (default 08:00 AM IST). |
| `ALERTS_WEBHOOK_URL` | *(empty)* | *(optional HTTPS URL)* | If set, posts daily alert digest summaries to the configured webhook endpoint. |
| `YFC_ADMIN_USERNAME` | `admin` | `admin` *(first boot only)* | Username of initial `SUPER_ADMIN` created only when `accounts` table is empty. |
| `YFC_ADMIN_PASSWORD` | *(empty)* | *(first boot only — then remove!)* | Initial `SUPER_ADMIN` password (12–72 UTF-8 bytes). Delete after first login. |

---

## 13. Appendix B — Switching RDS from Password Auth to IAM Auth Later

If you later want to eliminate `DB_PASSWORD` from `/etc/yfc/yfc.env` completely and authenticate to RDS using short-lived 15-minute IAM tokens generated automatically by your EC2 IAM Role (`yfc-ec2-role`):

### Step 1 — Enable IAM Authentication on RDS
In AWS Console → **RDS → `yfc-prod-db` → Modify**:
- Under **Database authentication**, select **Password and IAM database authentication**.
- Click **Continue** → **Apply immediately** → **Modify DB instance**.
- Under **Configuration**, copy the **`Resource ID`** (looks like `db-ABCDEFGHIJKLMNOPQRSTUVWX`).

### Step 2 — Create an IAM-Enabled PostgreSQL Role Inside `yfc`
Connect as master user `yfc` via `psql` from EC2 and run:
```sql
CREATE USER yfc_app WITH LOGIN;
GRANT rds_iam TO yfc_app;
GRANT CONNECT ON DATABASE yfc TO yfc_app;
GRANT USAGE, CREATE ON SCHEMA public TO yfc_app;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO yfc_app;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO yfc_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO yfc_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO yfc_app;
```
*(If transferring table ownership for future Flyway `ALTER TABLE` migrations on existing tables, also run `REASSIGN OWNED BY yfc TO yfc_app;` carefully or keep Flyway ownership aligned with `yfc_app`.)*

### Step 3 — Grant `rds-db:connect` to `yfc-ec2-role`
In AWS Console → **IAM → Roles → `yfc-ec2-role` → Add permissions → Create inline policy**:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "YfcRdsIamConnect",
      "Effect": "Allow",
      "Action": "rds-db:connect",
      "Resource": "arn:aws:rds-db:<your-aws-region>:<your-account-id>:dbuser:<db-resource-id>/yfc_app"
    }
  ]
}
```

### Step 4 — Update `/etc/yfc/yfc.env` & Restart
Replace the password lines in `/etc/yfc/yfc.env` with:
```dotenv
DB_AUTH_MODE=iam
AWS_REGION=<your-aws-region>
DB_USER=yfc_app
DB_POOL_MAX_SIZE=10
# Remove DB_PASSWORD completely!
```
Keep `DB_URL` unchanged (note that `IamEndpoint.java` strictly requires `.rds.amazonaws.com`, `sslmode=verify-full`, and `sslrootcert=...`—all already satisfied!). Then run:
```bash
sudo systemctl restart yfc-backend
sudo journalctl -u yfc-backend -f
```
`IamDatabaseConfiguration.java` will automatically initialize `yfc-iam-pool`, pull EC2 IMDSv2 credentials via `DefaultCredentialsProvider`, and mint a fresh IAM token for every new physical database connection with zero code changes.


---

## 14. Appendix C — Automated CI/CD Auto-Deploy via GitHub Actions (S3 + AWS SSM + OIDC)

This repository includes a ready-to-use automated deployment pipeline:
- **Workflow:** `.github/workflows/deploy-ec2.yml`
- **EC2 release script:** `deploy/ec2/apply-release.sh`

Whenever you run `git push origin main` (or trigger the workflow manually from the GitHub **Actions** tab), GitHub automatically:
1. Runs the full backend test suite against PostgreSQL 17 + H2 (`mvn -B -f backend/pom.xml clean verify`) and packages `yfc-backend.jar`.
2. Runs frontend dependency install (`npm ci`), unit tests (`npm test`), TypeScript check, and Vite production build (`npm run build`), archiving `frontend/dist/`.
3. **Only if BOTH jobs pass**, authenticates to AWS using **short-lived GitHub OIDC tokens** (zero static AWS keys, zero SSH keys!).
4. Uploads `yfc-backend.jar`, `frontend-dist.tar.gz`, and `deploy/ec2/apply-release.sh` to your private S3 bucket under `deployments/<timestamp>-<commit>/`.
5. Invokes **AWS Systems Manager (`AWS-RunShellScript`)** on your EC2 instance to download the release from S3, atomically activate `/opt/yfc/current.jar`, restart `yfc-backend`, poll `http://127.0.0.1:8080/actuator/health` until `{"status":"UP"}` (auto-rolling back the backend JAR if unhealthy!), sync `/var/www/yfc/`, and retain the newest 5 JARs.

### Why S3 + AWS SSM + OIDC instead of SSH (`scp`/`rsync`) from GitHub Actions?
- **No port 22 open to `0.0.0.0/0`:** GitHub-hosted runners use dynamic Azure IPs that change every run. Using SSH from GitHub would force you to open port 22 to the entire internet (contradicting our hardened `yfc-ec2-sg`). AWS SSM communicates over an outbound HTTPS channel initiated by `amazon-ssm-agent` inside EC2—**zero inbound ports needed!**
- **Zero static secrets in GitHub:** No `.pem` SSH private key and no permanent `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` stored in GitHub Secrets. GitHub OIDC mints a temporary IAM session valid only for that workflow run.
- **Automatic rollback on bad boot:** If a release fails to pass `/actuator/health` within 90 seconds, `apply-release.sh` automatically prints the last 80 lines of `journalctl -u yfc-backend` straight into GitHub Actions logs and rolls `/opt/yfc/current.jar` back to the previous working JAR without touching the live frontend.

---

### One-Time Setup (Takes ~10 Minutes)

#### Step 1 — Install AWS CLI v2 & `rsync` on EC2 (and Verify SSM Agent)
SSH into EC2 once and run:
```bash
sudo apt install -y rsync
sudo snap install aws-cli --classic
aws --version
sudo systemctl status snap.amazon-ssm-agent.amazon-ssm-agent.service --no-pager
```
- **Why `snap install aws-cli --classic`?** Official Ubuntu 24.04 EC2 AMIs preinstall `snapd` and `amazon-ssm-agent`, and the snap package installs official AWS CLI v2 cleanly on both ARM64 (`t4g.small`) and x86_64 (`t3.small`).

#### Step 2 — Update EC2's IAM Role (`yfc-ec2-role`)
In AWS Console → **IAM → Roles → `yfc-ec2-role` → Permissions**:
1. Click **Add permissions → Attach policies** → search and attach **`AmazonSSMManagedInstanceCore`**.
   - *(This allows EC2 to register as a managed node in AWS Systems Manager. Within ~2 minutes, verify it appears under **AWS Systems Manager → Fleet Manager** with Ping status = **Online**.)*
2. Click **Add permissions → Create inline policy → JSON** and name it `yfc-ec2-deploy-read-policy`:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Sid": "YfcReadDeploymentArtifacts",
         "Effect": "Allow",
         "Action": ["s3:GetObject"],
         "Resource": "arn:aws:s3:::yfc-prod-documents-<your-account-id>/deployments/*"
       }
     ]
   }
   ```
   - *(Notice we reuse the same private S3 bucket under a separate `deployments/*` prefix, completely isolated from `yfc-documents/*`!)*
3. *(Optional, recommended for cost hygiene)* In **S3 → your bucket → Management → Create lifecycle rule**:
   - Prefix: `deployments/`
   - Action: **Expire current versions of objects** after **`30` days** (and noncurrent versions after `7` days) so old release archives clean themselves up automatically.

#### Step 3 — Add GitHub as an OIDC Identity Provider in AWS IAM (Once per AWS Account)
In AWS Console → **IAM → Identity providers → Add provider**:
- **Provider type**: **OpenID Connect**
- **Provider URL**: `https://token.actions.githubusercontent.com`
- **Audience**: `sts.amazonaws.com`
- Click **Add provider**.

#### Step 4 — Create the GitHub Deploy IAM Role (`yfc-github-deploy-role`)
In AWS Console → **IAM → Roles → Create role**:
1. **Trusted entity type**: **Custom trust policy**.
2. Paste this trust policy (replace `<your-account-id>` with your 12-digit AWS account ID):
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Principal": {
           "Federated": "arn:aws:iam::<your-account-id>:oidc-provider/token.actions.githubusercontent.com"
         },
         "Action": "sts:AssumeRoleWithWebIdentity",
         "Condition": {
           "StringEquals": {
             "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
             "token.actions.githubusercontent.com:sub": "repo:manava10/YFCProjects:environment:production"
           }
         }
       }
     ]
   }
   ```
   > **CRITICAL OIDC DETAIL:** Because `.github/workflows/deploy-ec2.yml` sets `environment: production` on the `deploy-to-ec2` job, GitHub sets the OIDC `sub` claim to `repo:manava10/YFCProjects:environment:production` (NOT `ref:refs/heads/main`). Matching that exact string ensures AWS STS accepts the token—and locking the `production` GitHub environment to branch `main` (Step 5 below) guarantees no other branch or PR can assume this role.
3. Click **Next**, skip managed policies, click **Next**, name the role **`yfc-github-deploy-role`**, and click **Create role**.
4. Open **`yfc-github-deploy-role` → Add permissions → Create inline policy → JSON**, name it `yfc-github-deploy-policy`, and paste (replacing `<your-account-id>`, `<your-aws-region>`, `<your-ec2-instance-id>`):
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Sid": "UploadReleaseArtifacts",
         "Effect": "Allow",
         "Action": ["s3:PutObject"],
         "Resource": "arn:aws:s3:::yfc-prod-documents-<your-account-id>/deployments/*"
       },
       {
         "Sid": "TriggerDeployOnSpecificEc2",
         "Effect": "Allow",
         "Action": ["ssm:SendCommand"],
         "Resource": [
           "arn:aws:ssm:<your-aws-region>::document/AWS-RunShellScript",
           "arn:aws:ec2:<your-aws-region>:<your-account-id>:instance/<your-ec2-instance-id>"
         ]
       },
       {
         "Sid": "PollDeployCommandResult",
         "Effect": "Allow",
         "Action": ["ssm:GetCommandInvocation"],
         "Resource": "*"
       }
     ]
   }
   ```
   - *(Notice GitHub Actions can ONLY upload to `deployments/*` and run SSM on that exact EC2 instance ID—it cannot read your customer documents under `yfc-documents/*`, cannot access RDS, and cannot touch any other EC2 instance!)*
5. Copy the Role **ARN** (e.g., `arn:aws:iam::123456789012:role/yfc-github-deploy-role`).

#### Step 5 — Configure the `production` Environment in GitHub
In GitHub → **Repository (`manava10/YFCProjects`) → Settings → Environments → New environment**:
1. Name it **`production`** (exact lowercase spelling).
2. Under **Deployment branches and tags**, select **Selected branches and tags** → add branch rule **`main`**.
   - *(Optional: you can also enable **Required reviewers** if you want a manual "Approve deploy" button in GitHub before production updates!)*
3. Add **2 Environment Secrets** (Settings → Environments → `production` → **Add environment secret**):
   - `AWS_DEPLOY_ROLE_ARN` = `arn:aws:iam::<your-account-id>:role/yfc-github-deploy-role`
   - `EC2_INSTANCE_ID` = `i-0xxxxxxxxxxxxxxxx` *(your EC2 Instance ID from EC2 Console)*
4. Add **2 Environment Variables** (Settings → Environments → `production` → **Add environment variable**):
   - `AWS_REGION` = e.g. `ap-south-2` *(must match your EC2/S3/RDS region)*
   - `S3_DEPLOY_BUCKET` = `yfc-prod-documents-<your-account-id>`

#### Step 6 — Push to `main` and Watch It Deploy!
```bash
git push origin main
```
Open **GitHub → Actions → Deploy to AWS EC2** to watch:
1. `Test & Build Backend JAR` (runs full PostgreSQL 17 + H2 test suite + packages JAR)
2. `Test & Build Frontend Bundle` (runs `npm ci`, `npm test`, `npm run build`)
3. `Deploy to EC2 via S3 + AWS SSM` (uploads to S3 via OIDC, runs `apply-release.sh` on EC2, prints EC2 stdout/stderr, verifies `{"status":"UP"}`!)

*(You can also trigger a deployment any time without making a code commit via **GitHub → Actions → Deploy to AWS EC2 → Run workflow**.)*
