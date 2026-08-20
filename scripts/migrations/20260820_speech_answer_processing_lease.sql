ALTER TABLE public.speech_answers
    ADD COLUMN IF NOT EXISTS processing_token VARCHAR(36),
    ADD COLUMN IF NOT EXISTS lease_expires_at TIMESTAMPTZ;
