import { describe, expect, it } from 'vitest';
import { validateNewPassword } from './password.js';

describe('password bounds match the server BCrypt byte limit', () => {
  it('accepts twelve ASCII characters', () =>
    expect(() => validateNewPassword('a'.repeat(12))).not.toThrow());
  it('accepts exactly seventy-two ASCII bytes', () =>
    expect(() => validateNewPassword('a'.repeat(72))).not.toThrow());
  it('rejects passwords shorter than twelve characters', () =>
    expect(() => validateNewPassword('a'.repeat(11))).toThrow('at least 12'));
  it('rejects more than seventy-two ASCII bytes', () =>
    expect(() => validateNewPassword('a'.repeat(73))).toThrow('72 UTF-8 bytes'));
  it('accepts eighteen four-byte emoji', () =>
    expect(() => validateNewPassword('🔒'.repeat(18))).not.toThrow());
  it('rejects nineteen four-byte emoji even though they have fewer than seventy-two JavaScript characters', () =>
    expect(() => validateNewPassword('🔒'.repeat(19))).toThrow('72 UTF-8 bytes'));
});
