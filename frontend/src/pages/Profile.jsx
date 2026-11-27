import { useState } from 'react';
import { CheckCircle2, LockKeyhole, UserRound } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { request } from '../api/client.js';
import { Button, ErrorNotice, Field, Notice, PageHeading } from '../components/UI.jsx';
import { validateNewPassword } from '../lib/password.js';

export default function Profile() {
  const { user, setUser } = useAuth();
  const navigate = useNavigate();
  const [fullName, setFullName] = useState(user.fullName);
  const [passwords, setPasswords] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });
  const [saving, setSaving] = useState(false);
  const [changingPassword, setChangingPassword] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState(null);
  const [passwordError, setPasswordError] = useState(null);
  async function updateProfile(event) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      setUser(await request('/profile', { method: 'PUT', body: { fullName: fullName.trim() } }));
      setSaved(true);
    } catch (failure) {
      setError(failure);
    } finally {
      setSaving(false);
    }
  }
  async function changePassword(event) {
    event.preventDefault();
    setPasswordError(null);
    if (passwords.newPassword !== passwords.confirmPassword) {
      setPasswordError(new Error('The new passwords do not match.'));
      return;
    }
    setChangingPassword(true);
    try {
      validateNewPassword(passwords.newPassword);
      await request('/profile/password', {
        method: 'PUT',
        body: { currentPassword: passwords.currentPassword, newPassword: passwords.newPassword },
      });
      setUser(null);
      navigate('/login', { state: { message: 'Your password has changed. Sign in again.' } });
    } catch (failure) {
      setPasswordError(failure);
    } finally {
      setChangingPassword(false);
    }
  }
  return (
    <>
      <PageHeading
        eyebrow="ACCOUNT SETTINGS"
        title="Your profile"
        description="Keep your details current and your account protected."
      />
      <div className="profile-grid">
        <section className="panel form-panel">
          <div className="panel-heading">
            <h2>Personal details</h2>
            <UserRound size={22} />
          </div>
          <ErrorNotice error={error} />
          {saved && (
            <p className="saved-notice" role="status">
              <CheckCircle2 size={18} />
              Your details have been saved.
            </p>
          )}
          <form className="form-stack" onSubmit={updateProfile}>
            <Field
              label="Full name"
              id="fullName"
              name="fullName"
              autoComplete="name"
              value={fullName}
              onChange={(event) => {
                setFullName(event.target.value);
                setSaved(false);
              }}
              minLength={2}
              maxLength={80}
              required
            />
            <Field
              label="Phone number"
              id="profilePhone"
              value={user.phone}
              disabled
              hint="Your phone number identifies your wallet and cannot be changed here."
            />
            <Button type="submit" busy={saving}>
              Save changes
            </Button>
          </form>
        </section>
        <section className="panel form-panel">
          <div className="panel-heading">
            <h2>Change password</h2>
            <LockKeyhole size={22} />
          </div>
          <ErrorNotice error={passwordError} />
          <form className="form-stack" onSubmit={changePassword}>
            <Field
              label="Current password"
              id="currentPassword"
              name="currentPassword"
              type="password"
              autoComplete="current-password"
              value={passwords.currentPassword}
              onChange={(event) =>
                setPasswords({ ...passwords, currentPassword: event.target.value })
              }
              required
              maxLength={128}
            />
            <Field
              label="New password"
              id="newPassword"
              name="newPassword"
              type="password"
              autoComplete="new-password"
              value={passwords.newPassword}
              onChange={(event) => setPasswords({ ...passwords, newPassword: event.target.value })}
              minLength={12}
              maxLength={72}
              required
              hint="Use at least 12 characters, up to 72 UTF-8 bytes."
            />
            <Field
              label="Confirm new password"
              id="confirmPassword"
              name="confirmPassword"
              type="password"
              autoComplete="new-password"
              value={passwords.confirmPassword}
              onChange={(event) =>
                setPasswords({ ...passwords, confirmPassword: event.target.value })
              }
              required
              minLength={12}
              maxLength={72}
            />
            <Notice>
              Changing your password signs out all current sessions. You will need to sign in again.
            </Notice>
            <Button type="submit" busy={changingPassword}>
              Update password
            </Button>
          </form>
        </section>
      </div>
    </>
  );
}
