# MR.D.I.Y. Careers — Expanded Multi-Branch Workflow, Manager Routing & Interview Scheduler Blueprint

> 📌 **System Expansion & Defense Architecture Specification**
> 
> **Purpose:** Detailed operational blueprint for Multi-Role HR Admin vs. Branch Manager delegation, Per-Branch Onboarding Verification, and Interview Scheduling.

---

## 1. Executive Scope & System Assessment

### Current Implemented Baseline vs. Expanded Enterprise Workflow

| System Feature | Current Baseline Implementation | Proposed Expanded Enterprise Workflow |
|---|---|---|
| **HR User Role** | Central HR Admin (`public.admin_users`) handles all jobs, applications, and status updates. | **Multi-Role Hierarchy:**<br/>• **Central HR Admin:** Main gatekeeper & application screener.<br/>• **Branch Managers:** Dedicated store-level managers per branch. |
| **Application Routing** | Applications go directly to `public.applications` accessible by Central HR. | **Branch-Level Routing:**<br/>Central HR screens applications $\rightarrow$ Forwards shortlisted candidates to the specific **Branch Manager** assigned to that store location. |
| **Interview Scheduling** | HR sets application status to `Interview Scheduled` via text note. | **Interactive Interview Scheduler:**<br/>Dedicated calendar picker, venue selector (Store Branch or Head Office), mode selector (In-Person / Online), and FCM push invite to applicant phone tray. |
| **Pre-Employment Verification** | Applicant uploads documents; Central HR verifies them. | **Store-Level Onboarding Verification:**<br/>Branch Manager inspects government compliance documents (SSS, PhilHealth, Pag-IBIG, Medical) on day 1 of work and verifies benefits. |

---

## 2. Multi-Role Organizational Workflow & Delegation

```mermaid
graph TB
    subgraph "1. APPLICANT MOBILE SURFACE"
        APP[📱 Applicant submits application<br/>via Native Android App]
    end

    subgraph "2. CENTRAL HR ADMIN GATEKEEPER"
        HR[💻 Central HR Admin Dashboard]
        ML[🤖 Multi-Factor ML Screening<br/>& TF-IDF Match Evaluation]
        ROUTE{HR Decision}
    end

    subgraph "3. BRANCH MANAGER DELEGATION"
        BM_PASIG[🏬 Pasig Branch Manager]
        BM_TAYTAY[🏬 Taytay Branch Manager]
        BM_MAKATI[🏬 Makati Branch Manager]
    end

    subgraph "4. INTERVIEW & ONBOARDING"
        SCHED[📅 Interview Scheduler<br/>Date, Time, Branch Venue]
        ONBOARD[📋 Onboarding Checklist Verification<br/>SSS, PhilHealth, Pag-IBIG, Medical]
    end

    APP --> HR
    HR --> ML
    ML --> ROUTE
    ROUTE -->|Forward to Pasig| BM_PASIG
    ROUTE -->|Forward to Taytay| BM_TAYTAY
    ROUTE -->|Forward to Makati| BM_MAKATI
    
    BM_PASIG & BM_TAYTAY & BM_MAKATI --> SCHED
    SCHED -->|Conduct Interview & Hire| ONBOARD
```

---

## 3. Detailed Journey & Data Flow Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Candidate as Applicant (Mobile App)
    participant HR as Central HR Admin
    participant DB as Supabase PostgreSQL
    participant BM as Branch Manager (e.g. Taytay Branch)
    participant FCM as Push Notification / Realtime WS

    Candidate->>DB: Submits Job Application for "Cashier - Taytay Branch"
    DB-->>HR: Displays in Central HR Inbox
    HR->>HR: Evaluates ML Match Score & Resume Signals
    
    alt Candidate Qualified
        HR->>DB: Updates assigned_manager_id -> Taytay Branch Manager ID
        DB-->>BM: Candidate profile appears in Taytay Branch Manager Portal
        BM->>BM: Opens Interview Scheduler (Sets Date, Time, Venue)
        BM->>DB: Inserts row into public.interviews
        DB->>FCM: Dispatches FCM Push & Realtime WebSocket Alert
        FCM-->>Candidate: "Interview Scheduled at MR.D.I.Y. Taytay Branch on Friday 2 PM"
        
        Candidate->>BM: Attends Store Interview
        BM->>DB: Updates Application Status -> 'Hired'
        Candidate->>Candidate: Opens Pre-Employment Checklist in Mobile App
        Candidate->>DB: Uploads SSS, PhilHealth, Pag-IBIG & Medical Docs
        BM->>DB: Inspects & Verifies Onboarding Docs for Taytay Branch
    else Candidate Unqualified
        HR->>DB: Updates Status -> 'Rejected'
        DB->>FCM: Emits Notification to Candidate
    end
```

---

## 4. Enhanced Database Schema Design

To support per-branch managers, candidate routing, and interview scheduling, the database schema is expanded with the following tables and relationships:

```mermaid
erDiagram
    BRANCHES {
        uuid id PK
        text branch_name
        text city
        text address
        uuid manager_id FK
        timestamptz created_at
    }

    ADMIN_USERS {
        uuid id PK
        text username
        text password_hash
        text role
        uuid assigned_branch_id FK
        timestamptz created_at
    }

    APPLICATIONS {
        uuid id PK
        text user_id FK
        text job_id FK
        uuid assigned_branch_id FK
        uuid assigned_manager_id FK
        text status
        timestamptz created_at
    }

    INTERVIEWS {
        uuid id PK
        uuid application_id FK
        uuid interviewer_id FK
        timestamptz scheduled_at
        text mode
        text venue_address
        text status
        text notes
        timestamptz created_at
    }

    PRE_EMPLOYMENT_REQUIREMENTS {
        uuid id PK
        text user_id FK
        uuid application_id FK
        text requirement_name
        text status
        text document_url
        uuid verified_by_manager_id FK
        timestamptz updated_at
    }

    BRANCHES ||--o{ ADMIN_USERS : "assigned_to"
    BRANCHES ||--o{ APPLICATIONS : "targets"
    ADMIN_USERS ||--o{ APPLICATIONS : "manages"
    APPLICATIONS ||--o{ INTERVIEWS : "has_schedules"
    APPLICATIONS ||--o{ PRE_EMPLOYMENT_REQUIREMENTS : "requires_docs"
```

### SQL Table Specifications

```sql
-- 1. Store Branches Table
CREATE TABLE IF NOT EXISTS public.branches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_name TEXT NOT NULL,
    city TEXT NOT NULL,
    address TEXT NOT NULL,
    manager_id UUID,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 2. Enhanced Admin Users Table with Roles
ALTER TABLE public.admin_users 
ADD COLUMN IF NOT EXISTS role TEXT DEFAULT 'HR_ADMIN', -- 'HR_ADMIN' or 'BRANCH_MANAGER'
ADD COLUMN IF NOT EXISTS assigned_branch_id UUID REFERENCES public.branches(id);

-- 3. Applications Table Routing Columns
ALTER TABLE public.applications
ADD COLUMN IF NOT EXISTS assigned_branch_id UUID REFERENCES public.branches(id),
ADD COLUMN IF NOT EXISTS assigned_manager_id UUID REFERENCES public.admin_users(id);

-- 4. Dedicated Interview Scheduler Table
CREATE TABLE IF NOT EXISTS public.interviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES public.applications(id) ON DELETE CASCADE,
    interviewer_id UUID REFERENCES public.admin_users(id),
    scheduled_at TIMESTAMPTZ NOT NULL,
    mode TEXT DEFAULT 'In-Person', -- 'In-Person' or 'Online'
    venue_address TEXT NOT NULL,
    status TEXT DEFAULT 'Scheduled', -- 'Scheduled', 'Completed', 'Rescheduled', 'Cancelled'
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);
```

---

## 5. Interview Scheduler Specification

### Admin Web Interface (`mrdiy-admin`)
1. **Calendar Picker Component:**
   - Date picker & Time slot selector (e.g., *October 15, 2026 at 2:00 PM*).
2. **Venue Selector:**
   - Option A: *Branch Store Address* (auto-populated from job branch e.g., *MR.D.I.Y. Taytay Branch, Manila East Road*).
   - Option B: *Head Office / Online Video Link*.
3. **Interviewer Assignment:**
   - Dropdown listing assigned Branch Manager or HR Officer.
4. **Automated Invite Dispatch:**
   - Sends Realtime CDC WebSocket payload + FCM Push Notification + Confirmation Email to candidate.

### Mobile App Surface (`mrdiy-careers`)
1. **Interview Card in `ApplicationsFragment`:**
   - Displays scheduled date, countdown, mode badge (*In-Person / Online*), and branch venue address.
   - One-tap *"Add to Calendar"* / *"View Branch Location Map"*.
2. **Push Notification Banner:**
   - *"MR.D.I.Y. Interview Invitation: Friday Oct 15, 2:00 PM at Pasig Branch."*

---

## 6. How to Present This in Capstone Defense (Panel Script & Strategy)

When the panel asks: **"Does your system handle multi-branch store managers and interview scheduling?"**, use this explanation strategy:

> 🗣️ **Defense Script for Presenters:**
>
> *"Yes, Panel! The system architecture was engineered with a two-tier enterprise recruitment workflow in mind:*
>
> 1. **Central HR Gatekeeper Layer:** All incoming job applications pass through Central HR Admin where our **Multi-Factor ML Resume Validator** and **TF-IDF Cosine Similarity Engine** filter out spam documents and rank qualified applicants.
>
> 2. **Branch Manager Delegation Layer:** Once Central HR shortlists a candidate, the applicant profile is routed to the specific **Branch Manager** assigned to that store (e.g. Pasig or Taytay Branch).
>
> 3. **Store-Level Interview & Onboarding:** The Branch Manager uses the **Interview Scheduler** to invite the applicant to a store interview and verifies their government onboarding requirements (SSS, PhilHealth, Pag-IBIG, Medical) on day 1 of employment.
>
> *Our current functional prototype establishes the core ML engine, real-time WebSocket infrastructure, and database schema, while this blueprint specifies the multi-tenant branch routing for full commercial deployment."*
