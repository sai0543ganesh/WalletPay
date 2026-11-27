export function validateNewPassword(password) {
  if (password.length < 12) throw new Error('Use a password with at least 12 characters.');
  if (new TextEncoder().encode(password).length > 72) {
    throw new Error(
      'This password is too long. Use no more than 72 UTF-8 bytes; accented characters and emoji use more than one byte.',
    );
  }
}
