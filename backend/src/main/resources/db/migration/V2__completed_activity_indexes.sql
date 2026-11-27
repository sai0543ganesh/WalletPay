CREATE INDEX transactions_sender_completed_idx ON wallet_transactions(sender_id, completed_at DESC) WHERE status = 'COMPLETED';
CREATE INDEX transactions_recipient_completed_idx ON wallet_transactions(recipient_id, completed_at DESC) WHERE status = 'COMPLETED';
