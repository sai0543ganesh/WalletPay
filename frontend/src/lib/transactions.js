export function transactionTypeLabel(transaction) {
  if (transaction.type === 'DEMO_CREDIT') return 'Welcome demo credit';
  if (transaction.type === 'DEPOSIT') return 'Sandbox deposit';
  return 'Wallet transfer';
}

export function receiptDirectionLabel(transaction) {
  if (transaction.type === 'DEMO_CREDIT') return 'Welcome demo credit';
  if (transaction.type === 'DEPOSIT')
    return transaction.status === 'COMPLETED' ? 'Deposit completed' : 'Deposit requested';
  return transaction.direction === 'DEBIT' ? 'Sent' : 'Received';
}
