ALTER TABLE wallets ADD COLUMN demo_welcome_claimed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE wallet_transactions DROP CONSTRAINT wallet_transactions_type_check;
ALTER TABLE wallet_transactions ADD CONSTRAINT wallet_transactions_type_check
    CHECK (type IN ('TRANSFER', 'DEPOSIT', 'DEMO_CREDIT'));

ALTER TABLE wallet_transactions DROP CONSTRAINT transaction_parties;
ALTER TABLE wallet_transactions ADD CONSTRAINT transaction_parties CHECK (
    (type = 'TRANSFER' AND sender_id <> recipient_id AND status = 'COMPLETED')
    OR (type = 'DEPOSIT' AND sender_id = recipient_id)
    OR (type = 'DEMO_CREDIT' AND sender_id = recipient_id AND status = 'COMPLETED' AND amount_minor = 10000)
);

-- Defense in depth: the application also serializes claims with the wallet row lock.
CREATE UNIQUE INDEX transactions_one_demo_credit_per_account
    ON wallet_transactions (recipient_id) WHERE type = 'DEMO_CREDIT';
