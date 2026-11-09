/**
 * Only same-app paths are allowed after sign-in, so a crafted link like
 * `/login?returnUrl=//evil.example` can't send the user to another site.
 */
export function safeReturnUrl(value: string | null | undefined): string {
  if (!value || !value.startsWith('/')) {
    return '/';
  }
  // Browsers treat "//host" and "/\host" as other sites, and strip tabs and newlines first.
  if (/^\/[/\\]/.test(value) || /[\\\s]/.test(value) || hasControlCharacter(value)) {
    return '/';
  }
  if (value === '/login' || value.startsWith('/login?') || value.startsWith('/signup')) {
    return '/';
  }
  return value;
}

function hasControlCharacter(value: string): boolean {
  for (let i = 0; i < value.length; i++) {
    const code = value.charCodeAt(i);
    if (code < 0x20 || code === 0x7f) {
      return true;
    }
  }
  return false;
}
