# Admin (`mrdiy-admin`) and Android (`mrdiy-careers`) Integration Blueprint

This document details the shared database structure, API contracts, real-time WebSocket channels, and cross-platform synchronization between the **Admin Web Dashboard (Next.js)** and the **Applicant Mobile App (Android Native Kotlin)**.

---

## Shared Supabase Backend & Credentials

* **Supabase Project URL:** `https://xbsrnnuhepljpvzewvsp.supabase.co`
* **Anon Key (Android App & Web Public):** Configured in `SupabaseProvider.kt` and `NEXT_PUBLIC_SUPABASE_ANON_KEY`.
* **Service Role Key (Admin Server Only):** Used by Next.js server actions / API routes for admin queries and sending notifications. Never exposed to Android client.

---

## Recent Integration Commits & Updates

### 1. Admin Navigation & Fast Routing
* **Next.js Link Component (`cb3b3dc`):** Updated all admin sidebar/navbar navigation buttons from traditional page reloads to Next.js `Link` components (`next/link`), enabling client-side single-page transition for HR administrators.

### 2. Unified Applications Table & Status Synchronization
* **Schema Unification (`202610110003_unify_applications_table.sql`):**
  * Android and Admin now use a single `public.applications` table.
  * Captures `user_id`, `job_id`, `status` (`Pending`, `Shortlisted`, `Interview Scheduled`, `Hired`, `Rejected`), `candidate_name`, `candidate_email`, `candidate_phone`, and `resume_url`.
  * RLS Policy: Applicants can insert and read their own applications; Service Role (Admin) has full read/write access.

### 3. Email Notification Logging (`public.notification_emails`)
* **Email Log Migration (`202610110008_push_tokens_and_email_log.sql`):**
  * Admin backend logs every HR status email sent via Gmail SMTP (`nodemailer`) into `public.notification_emails`.
  * Keeps audit logs of recipient `to_email`, `subject`, `status` (`sent` / `failed`), and `error` messages for HR inspection.

### 4. Push Token Registration (`public.push_tokens`)
* **Firebase Cloud Messaging (FCM) Sync:**
  * Android client registers its FCM token upon sign-in via `PushTokenRegistrar.kt` into `public.push_tokens` (`token`, `user_id`, `platform`, `updated_at`).
  * Enables background push notifications to applicant device trays even when the app is closed.

### 5. Real-Time Application & Alert Streaming
* **Postgres CDC WebSockets:**
  * Android listens to live Realtime channels on `public.applications` and `public.alerts`.
  * Status updates made by HR in Next.js Admin reflect immediately on candidate phone UI with color-coded badges without page refresh.

---

## Summary of Integration Contracts

| Operation | Admin Action (Next.js) | Mobile Action (Android) | DB Table / Realtime Event |
|---|---|---|---|
| **Publish Job** | Creates/updates job with status=`published` | Listens to catalog polling / realtime | `public.jobs` |
| **Apply for Job** | Receives application in `/applications` | Submits application via `ApplicationsRepository.kt` | `public.applications` |
| **Update Status** | HR changes status to `Shortlisted`/`Hired` | Realtime WebSocket updates UI badge | `public.applications` |
| **Send Message** | HR sends message in candidate drawer | App fetches/receives in `MessagesFragment.kt` | `public.messages` |
| **Create Alert** | Triggers alert record on status change | Bell icon badge updates live | `public.alerts` |
| **Push Token** | Admin server reads tokens to trigger push | App registers FCM token on login | `public.push_tokens` |
