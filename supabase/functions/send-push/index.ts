import { createClient } from "npm:@supabase/supabase-js@2";

type WebhookPayload = {
  type: "INSERT" | "UPDATE" | "DELETE";
  table: string;
  schema: string;
  record: Record<string, unknown> | null;
  old_record: Record<string, unknown> | null;
};

type ServiceAccount = {
  project_id: string;
  client_email: string;
  private_key: string;
  token_uri?: string;
};

const encoder = new TextEncoder();

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

function encodeJson(value: unknown): string {
  return base64Url(encoder.encode(JSON.stringify(value)));
}

function pemBytes(pem: string): Uint8Array {
  const encoded = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replaceAll(/\s/g, "");
  return Uint8Array.from(atob(encoded), (character) => character.charCodeAt(0));
}

async function firebaseAccessToken(account: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const tokenUri = account.token_uri ?? "https://oauth2.googleapis.com/token";
  const unsigned = `${encodeJson({ alg: "RS256", typ: "JWT" })}.${encodeJson({
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: tokenUri,
    iat: now,
    exp: now + 3600,
  })}`;

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemBytes(account.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    encoder.encode(unsigned),
  ));
  const assertion = `${unsigned}.${base64Url(signature)}`;
  const response = await fetch(tokenUri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  const result = await response.json();
  if (!response.ok || !result.access_token) throw new Error("Firebase authorization failed");
  return result.access_token as string;
}

function adminKey(): string {
  const currentKeys = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (currentKeys) return JSON.parse(currentKeys).default;
  const legacyKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!legacyKey) throw new Error("Supabase server key is unavailable");
  return legacyKey;
}

Deno.serve(async (request) => {
  try {
    if (request.method !== "POST") return Response.json({ error: "Method not allowed" }, { status: 405 });

    const expectedSecret = Deno.env.get("PUSH_WEBHOOK_SECRET");
    if (!expectedSecret || request.headers.get("x-webhook-secret") !== expectedSecret) {
      return Response.json({ error: "Unauthorized" }, { status: 401 });
    }

    const payload = await request.json() as WebhookPayload;
    const record = payload.record ?? {};
    const previous = payload.old_record ?? {};
    let title = "MR.D.I.Y. Careers";
    let body = "You have a new career update.";
    let targetUserId: string | null = null;
    const data: Record<string, string> = { type: "career_update" };

    if (payload.schema === "public" && payload.table === "jobs" &&
      (payload.type === "INSERT" || payload.type === "UPDATE")) {
      const status = String(record.status ?? "").toLowerCase();
      const previousStatus = String(previous.status ?? "").toLowerCase();
      if (status !== "published" || previousStatus === "published") {
        return Response.json({ ignored: true, reason: "Job is not newly published" });
      }
      const jobTitle = String(record.title ?? "New job opportunity");
      title = "New Job Opportunity";
      body = `${jobTitle} is now available at MR.D.I.Y.`;
      data.type = "job_published";
      data.job_id = String(record.id ?? "");
    } else if (payload.schema === "public" &&
      (payload.table === "job_applications" || payload.table === "applications") &&
      payload.type === "UPDATE") {
      const status = String(record.status ?? "Updated");
      if (status === String(previous.status ?? "")) {
        return Response.json({ ignored: true, reason: "Application status did not change" });
      }
      targetUserId = String(record.user_id ?? "");
      if (!targetUserId) return Response.json({ ignored: true, reason: "Application has no user" });
      title = "Application Update";
      body = `Your application status is now ${status}.`;
      data.type = "application_update";
      data.application_id = String(record.id ?? "");
      data.job_id = String(record.job_id ?? "");
      data.status = status;
    } else {
      return Response.json({ ignored: true, reason: "Unsupported database event" });
    }

    const supabase = createClient(Deno.env.get("SUPABASE_URL")!, adminKey(), {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    let tokenQuery = supabase.from("push_tokens").select("token");
    if (targetUserId) tokenQuery = tokenQuery.eq("user_id", targetUserId);
    const { data: tokenRows, error: tokenError } = await tokenQuery;
    if (tokenError) throw tokenError;
    const tokens = [...new Set((tokenRows ?? []).map((row) => row.token).filter(Boolean))];
    if (tokens.length === 0) return Response.json({ sent: 0, reason: "No registered devices" });

    const serviceAccount = JSON.parse(
      Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON")!,
    ) as ServiceAccount;
    const accessToken = await firebaseAccessToken(serviceAccount);
    const results = await Promise.all(tokens.map(async (token) => {
      const response = await fetch(
        `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            message: {
              token,
              data: { title, body, ...data },
              android: { priority: "HIGH" },
            },
          }),
        },
      );
      return response.ok;
    }));

    return Response.json({ sent: results.filter(Boolean).length, failed: results.filter((ok) => !ok).length });
  } catch (error) {
    console.error(error instanceof Error ? error.message : "Unknown push error");
    return Response.json({ error: "Push delivery failed" }, { status: 500 });
  }
});
