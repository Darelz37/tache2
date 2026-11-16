CREATE TABLE IF NOT EXISTS schema_version (version int PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email text UNIQUE NOT NULL,
  name text NOT NULL,
  password_hash text NOT NULL,
  role text NOT NULL CHECK (role IN ('operator','admin')),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS sessions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code text UNIQUE NOT NULL CHECK (code ~ '^[A-Za-z0-9-]{3,30}$'),
  title text NOT NULL,
  status text NOT NULL DEFAULT 'draft' CHECK (status IN ('draft','scheduled','published','unpublished')),
  publish_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS candidates (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id uuid NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  matricule text NOT NULL,
  full_name text NOT NULL,
  dob date NOT NULL,
  UNIQUE (session_id, matricule)
);
CREATE TABLE IF NOT EXISTS results (
  candidate_id uuid PRIMARY KEY REFERENCES candidates(id) ON DELETE CASCADE,
  decision text NOT NULL CHECK (decision IN ('ADMIS','AJOURNE','ABSENT')),
  average numeric(4,2) CHECK (average BETWEEN 0 AND 20),
  mention text CHECK (mention IN ('PASSABLE','ASSEZ BIEN','BIEN','TRES BIEN')),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS imports (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id uuid NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  filename text NOT NULL,
  object_key text NOT NULL,
  error_key text,
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','processing','done','failed')),
  rows_total int NOT NULL DEFAULT 0,
  rows_ok int NOT NULL DEFAULT 0,
  rows_error int NOT NULL DEFAULT 0,
  errors_truncated boolean NOT NULL DEFAULT false,
  created_by uuid REFERENCES users(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  started_at timestamptz,
  heartbeat_at timestamptz,
  finished_at timestamptz
);
CREATE INDEX IF NOT EXISTS imports_pending ON imports (status, created_at);
CREATE INDEX IF NOT EXISTS imports_session ON imports (session_id, created_at);
CREATE TABLE IF NOT EXISTS audit_log (
  id bigserial PRIMARY KEY,
  at timestamptz NOT NULL DEFAULT now(),
  user_id uuid REFERENCES users(id),
  action text NOT NULL,
  target text,
  details jsonb
);
CREATE TABLE IF NOT EXISTS worker_status (name text PRIMARY KEY, last_run timestamptz NOT NULL);
