create extension if not exists pgcrypto;

create table accounts (
    id uuid primary key default gen_random_uuid(),
    email varchar(255) not null,
    password_hash varchar(255),
    name varchar(50) not null,
    role varchar(20) not null,
    status varchar(20) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint uk_accounts_email unique (email),
    constraint ck_accounts_role
        check (role in ('GUARDIAN', 'ADMIN')),
    constraint ck_accounts_status
        check (status in ('ACTIVE', 'BLOCKED', 'WITHDRAWN'))
);

create table oauth_identities (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null,
    provider varchar(30) not null,
    provider_user_id varchar(255) not null,
    created_at timestamptz not null default now(),
    last_login_at timestamptz,

    constraint fk_oauth_identities_account
        foreign key (account_id)
        references accounts(id)
        on delete cascade,
    constraint uk_oauth_provider_user
        unique (provider, provider_user_id),
    constraint uk_oauth_account_provider
        unique (account_id, provider)
);

create table refresh_tokens (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null,
    token_hash varchar(64) not null,
    expires_at timestamptz not null,
    revoked boolean not null default false,
    created_at timestamptz not null default now(),

    constraint fk_refresh_tokens_account
        foreign key (account_id)
        references accounts(id)
        on delete cascade,
    constraint uk_refresh_token_hash unique (token_hash)
);

create table oauth_login_codes (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null,
    code_hash varchar(64) not null,
    expires_at timestamptz not null,
    used_at timestamptz,
    created_at timestamptz not null default now(),

    constraint fk_oauth_login_codes_account
        foreign key (account_id)
        references accounts(id)
        on delete cascade,
    constraint uk_oauth_login_code_hash unique (code_hash)
);

create index idx_oauth_identities_account_id
    on oauth_identities(account_id);

create index idx_refresh_tokens_account_id
    on refresh_tokens(account_id);

create index idx_refresh_tokens_expires_at
    on refresh_tokens(expires_at);

create index idx_oauth_login_codes_expires_at
    on oauth_login_codes(expires_at);
