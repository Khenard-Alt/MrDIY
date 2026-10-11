# MR.D.I.Y. Careers — Overall System Architecture & Data Flow

This document details the complete end-to-end system architecture, entity relationships, and operational data flows connecting the **Admin Web Dashboard**, **Android Mobile App**, **Machine Learning Engine**, and **Supabase Backend**.

---

## 1. System Architecture Diagram

```mermaid
graph TB
    subgraph "FRONTEND CLIENTS"
        MOBILE["📱 Applicant Mobile App (Android)<br/>• Native Kotlin / ViewBinding / Coroutines<br/>• TF-IDF + Cosine Matching Engine<br/>• Multi-Factor ML Resume Validator<br/>• Universal Search (Metro Manila Scope)<br/>• Realtime WebSocket Channels"]
        ADMIN["💻 HR Admin Web Dashboard (Next.js)<br/>• React / Tailwind CSS / Supabase JS SDK<br/>• Job Publishing & Application Review<br/>• AI Job Description Generator<br/>• Candidate Diagnostic & Velocity Analytics"]
    end

    subgraph "SUPABASE CLOUD INFRASTRUCTURE"
        AUTH["🔑 Supabase Auth<br/>• Email / Password / Deep Links<br/>• Phone OTP Verification"]
        DB[( PostgreSQL Database )]
        STORAGE["🗄️ Supabase Storage<br/>• Resumes Bucket (Private RLS)<br/>• Avatars Bucket (Public/Owner RLS)"]
        REALTIME["⚡ Realtime Engine (CDC WebSockets)<br/>• Jobs broadcast<br/>• Applications live stream<br/>• Alerts realtime channel"]
    end

    subgraph "NOTIFICATION & BACKGROUND SERVICES"
        FCM["🔔 Firebase Cloud Messaging (FCM)<br/>• Push Notifications"]
        SMTP["✉️ Gmail SMTP / Nodemailer<br/>• HR Notification Emails"]
    end

    MOBILE -->|Auth Requests| AUTH
    ADMIN -->|Admin Auth| AUTH
    MOBILE -->|PostgREST Queries / RLS| DB
    ADMIN -->|Service Role / RLS Queries| DB
    MOBILE -->|Upload Resume / Avatar| STORAGE
    MOBILE <-->|WebSockets Listener| REALTIME
    ADMIN <-->|WebSockets Broadcast| REALTIME
    DB -->|CDC Events| REALTIME
    ADMIN -->|Send Email| SMTP
    DB -->|Push Tokens| FCM
```

---

## 2. Complete End-to-End System Journeys

### Journey 1: HR Admin Posts & Publishes a Job

```mermaid
sequenceDiagram
    autonumber
    actor HR as HR Administrator
    participant Admin as Admin Web Portal (Next.js)
    participant SB as Supabase Database (PostgreSQL)
    participant RT as Supabase Realtime WebSocket
    participant App as Android Mobile App

    HR->>Admin: Fills Job Posting Form & Clicks "Publish"
    Note over Admin: Optionally uses "✨ Generate AI Description"
    Admin->>SB: INSERT / UPDATE into public.jobs (status = 'published')
    SB-->>Admin: Returns Success (Job ID)
    SB->>RT: CDC Trigger (INSERT/UPDATE event on public.jobs)
    RT-->>App: Emits Realtime Event
    App->>App: Updates Job Catalog UI automatically
```

---

### Journey 2: Candidate Registration & Universal Email Verification

```mermaid
sequenceDiagram
    autonumber
    actor User as Applicant
    participant App as Android App (RegisterFragment)
    participant Auth as Supabase Auth Engine
    participant Email as Candidate Email Inbox
    participant App2 as MainActivity (mrdiy://login-callback)

    User->>App: Inputs Full Name, Email, Password & Accepts T&C
    Note over App: Validates email format (.edu, @gmail.com, corporate)
    App->>Auth: signUpWith(Email, Metadata)
    Auth-->>Email: Sends Verification Link containing token
    User->>Email: Taps Verification Link
    Email->>App2: Deep-link redirect to mrdiy://login-callback
    App2->>Auth: Exchange Auth Code for Active Session
    Auth-->>App2: Returns Session & JWT
    App2->>App2: Saves current user session & Navigates to Home
```

---

### Journey 3: Candidate Uploads Resume & Multi-Factor ML Content Validation

```mermaid
sequenceDiagram
    autonumber
    actor User as Applicant
    participant App as ResumeUploadFragment
    participant PDF as PdfBox-Android Text Extractor
    participant ML as ResumeValidator / ResumeSkills
    participant Storage as Supabase Storage (resumes)
    participant DB as Supabase public.profiles

    User->>App: Selects Resume Document (PDF / DOCX / TXT)
    App->>PDF: Extract raw text content (Max 10MB, <=100 pages)
    PDF-->>App: Raw Extracted Text
    App->>ML: Run Multi-Factor Validation
    Note over ML: Checks Section Headers, Contact Details,<br/>Timeline Patterns & Skill Vocabulary
    alt Invalid Document (Recipe, Invoice, Story)
        ML-->>App: Validation Failed (Missing resume signals)
        App-->>User: Show Error: "File does not appear to be a valid resume"
    else Valid Resume Document
        ML-->>App: Validation Passed (Extracted Skills & Years)
        App->>Storage: Upload to private path: {user_id}/{filename}
        Storage-->>App: Return File Path
        App->>DB: Merge parsed skills, experience & resume path into profiles
        DB-->>App: Profile Updated
        App-->>User: Show Success & Trigger Recommendation Refresh
    end
```

---

### Journey 4: Intelligent Job Matching & Universal Search Engine

```mermaid
flowchart TD
    A[Candidate Profile / Extracted Resume] --> B[Construct Candidate Text Vector]
    C[Published Job Catalog] --> D[Construct Document Term Vectors]
    
    B & D --> E[TF-IDF Term Weighting Engine]
    E --> F[Cosine Similarity Metric Calculation]
    F --> G["Match Score Percentage (0 - 100%)"]
    
    G --> H{Score Category}
    H -->|">= 75%"| I["🌟 Excellent Match"]
    H -->|"50% - 74%"| J["👍 Good Match"]
    H -->|"< 50%"| K["📄 Fair / Low Match"]
    
    G --> L[Explainable AI XAI Engine]
    L --> M[Matched Skill Chips + Missing Skill Suggestions]
    
    N[User Search Query e.g. 'casher'] --> O[Typo-Tolerant Intent Parser -> 'cashier']
    O --> P[Metro Manila Regional Scope Filter]
    P --> Q[Hybrid Search Ranker: 75% Term Relevance + 25% Profile Fit]
    Q --> R[Display Search Results to Applicant]
```

---

### Journey 5: Application Submission & Live WebSocket Status Tracker

```mermaid
sequenceDiagram
    autonumber
    actor Candidate as Applicant
    participant App as Android JobDetailFragment
    participant DB as Supabase public.applications
    participant Admin as Admin Web Portal
    participant RT as Realtime WebSocket Listener

    Candidate->>App: Taps "Apply Now"
    Note over App: Mutex lock prevents double-submission
    App->>DB: INSERT into public.applications (user_id, job_id, status='Pending')
    DB-->>App: Success
    
    Note over DB,RT: Realtime WebSocket Channel active
    Admin->>DB: HR Updates Status ('Pending' -> 'Shortlisted' / 'Hired')
    DB->>RT: CDC Broadcast Event
    RT-->>App: Realtime Applications Channel Payload
    App->>App: Live Update Badge on Applications UI without refresh!
```

---

## 3. Entity-Relationship Diagram (Database Schema ERD)

```mermaid
erDiagram
    ADMIN_USERS {
        uuid id PK
        text username
        text password_hash
        timestamptz created_at
    }

    PROFILES {
        text user_id PK
        text email
        text full_name
        text phone
        text headline
        text about
        text location
        text skills
        jsonb education
        jsonb work_experience
        text resume_url
        text avatar_url
        timestamptz updated_at
    }

    JOBS {
        bigint id PK
        text title
        text description
        text location
        text branch
        text salary_range
        text salary_period
        text job_type
        text category
        text experience_level
        text status
        timestamptz created_at
    }

    APPLICATIONS {
        uuid id PK
        text user_id FK
        text job_id FK
        text status
        text candidate_name
        text candidate_email
        text candidate_phone
        text resume_url
        timestamptz created_at
        timestamptz updated_at
    }

    SAVED_JOBS {
        uuid id PK
        text user_id FK
        text job_id FK
        timestamptz created_at
    }

    ALERTS {
        uuid id PK
        text user_id FK
        text type
        text title
        text message
        boolean is_read
        text job_id
        timestamptz created_at
    }

    PRE_EMPLOYMENT_REQUIREMENTS {
        uuid id PK
        text user_id FK
        uuid application_id FK
        text requirement_name
        text status
        text document_url
        timestamptz updated_at
    }

    NOTIFICATION_EMAILS {
        uuid id PK
        text user_id
        text application_id
        text to_email
        text subject
        text status
        text error
        timestamptz created_at
    }

    PUSH_TOKENS {
        text token PK
        text user_id
        text platform
        timestamptz updated_at
    }

    PROFILES ||--o{ APPLICATIONS : "submits"
    JOBS ||--o{ APPLICATIONS : "receives"
    PROFILES ||--o{ SAVED_JOBS : "bookmarks"
    JOBS ||--o{ SAVED_JOBS : "saved_in"
    PROFILES ||--o{ ALERTS : "receives"
    PROFILES ||--o{ PRE_EMPLOYMENT_REQUIREMENTS : "submits_docs"
    APPLICATIONS ||--o{ PRE_EMPLOYMENT_REQUIREMENTS : "tracks_docs"
    PROFILES ||--o{ PUSH_TOKENS : "registers"
```

---

## 4. Table of Database Entities & Security Policies

| Table / Bucket | Primary Key / Path | RLS Access Policy | Purpose |
| :--- | :--- | :--- | :--- |
| `public.jobs` | `id` (BigInt/Text) | Read: Public / Applicant<br/>Write: Service Role (Admin) | Published job postings catalog |
| `public.profiles` | `user_id` (Text) | Read/Write: Owner (`auth.uid()::text = user_id`) | Candidate profile info, skills, education, experience, avatar |
| `public.applications` | `id` (UUID) | Read/Write: Owner (`auth.uid()::text = user_id`) & Service Role | Submitted job applications & live status tracking |
| `public.saved_jobs` | `id` (UUID) | Read/Write: Owner (`auth.uid()::text = user_id`) | Bookmarked jobs per applicant |
| `public.alerts` | `id` (UUID) | Read/Write: Owner (`auth.uid()::text = user_id`) & Service Role | Notifications and application status alerts |
| `public.pre_employment_requirements` | `id` (UUID) | Read/Write: Owner (`auth.uid()::text = user_id`) & Service Role | Government compliance documents onboarding checklist |
| `public.notification_emails` | `id` (UUID) | Service Role Only | Email delivery logs for HR notification tracking |
| `public.push_tokens` | `token` (Text) | Read/Write: Owner (`auth.uid()::text = user_id`) & Service Role | Firebase Cloud Messaging (FCM) device push tokens |
| Storage: `resumes` | `{user_id}/{filename}` | Read/Write: Owner (`auth.uid()::text = user_id`) | Private uploaded PDF/DOCX/TXT resume files |
| Storage: `avatars` | `{user_id}/{filename}` | Read: Public / Write: Owner | Candidate profile picture uploads |
