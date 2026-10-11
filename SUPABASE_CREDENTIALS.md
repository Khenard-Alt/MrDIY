# Supabase Credentials, Google OAuth & System Email Configuration

> [!IMPORTANT]
> This file contains project database credentials, API keys, Google OAuth platform credentials, Admin login credentials, and notification SMTP details. Full raw keys are stored locally in `.env` and `.env.local` (gitignored).

---

## Admin Web Panel Credentials (`http://localhost:3000/login`)

* **Username:** `admin`
* **Password:** `admin123`
* **Database Table:** `public.admin_users`

---

## Supabase Project Details

* **Organization:** `Project-sia`
* **Project Name:** `mr_diyDB`
* **Region:** `Asia-Pacific`
* **Database Password:** `Admin@123!!159753`

---

## API Configuration

* **Project URL:** `https://xbsrnnuhepljpvzewvsp.supabase.co`
* **Publishable / Anon Key:** `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inhic3JubnVoZXBsanB2emV3dnNwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE2NTU4NDcsImV4cCI6MjEwNzIzMTg0N30.-TrtGsQDfh9x3HZIQkSMjvrm5id-Dwn8VFx4j4NkWOw`
* **Service Role Key:** `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inhic3JubnVoZXBsanB2emV3dnNwIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc5MTY1NTg0NywiZXhwIjoyMTA3MjMxODQ3fQ.NggQiC4Op5dKj7qjoJkxjcPTdFo2SIZjQUXF0rEzedY`

---

## Google OAuth Platform Credentials (For Supabase Auth Provider)

> [!NOTE]
> Configured in **Supabase Dashboard $\rightarrow$ Authentication $\rightarrow$ Providers $\rightarrow$ Google** to enable Google Sign-In in the mobile app and web portal.
> Full unmasked secrets are stored locally in `.env` and `.env.local`.

* **Client ID:** `653064003625-it8c7lhsrl778rcs6cne0p1c1k3ct34m.apps.googleusercontent.com`
* **Client Secret:** `GOCSPX-xFjt4-[LOCAL_ENV_SECRET_STORED_IN_DOTENV]`
* **Creation Date:** `October 12, 2026, 1:32:37 AM GMT+8`
* **Status:** `Enabled`

---

## System Gmail Account & SMTP Settings

* **System Gmail Address:** `mrdiy.careers.portal@gmail.com`
* **Gmail Account Password:** `Admin@123!!`
* **SMTP Host:** `smtp.gmail.com`
* **SMTP Port:** `587`
* **Google App Password:** `oljf zxry gnaj mkkh`
