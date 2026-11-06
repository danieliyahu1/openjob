---
name: email
description: Use when needing to check email for verification codes, confirmation links, or identity verification emails.
---

# Email Verification

## Credentials

The mailbox credentials are in the environment. Read them with a shell command
and type them into the form; never print the values back into the conversation.

### Primary (Gmail)

- Email: `EMAIL_PRIMARY_ADDRESS`
- Password: `EMAIL_PRIMARY_PASSWORD`

### Secondary (Zoho Mail)

- Email: `EMAIL_SECONDARY_ADDRESS`
- Password: `EMAIL_SECONDARY_PASSWORD`

## Workflow

**Browser automation:** All web navigation and interaction in this skill uses the
OpenClaw `browser` tool — not Playwright MCP. There is no `browser_navigate` or
`browser_snapshot` here. Use:

| Need | Call |
| --- | --- |
| Open webmail | `browser` `{action:"open", url:"https://mail.google.com"}` |
| Structure + refs | `browser` `{action:"snapshot", interactive:true}` |
| Click | `browser` `{action:"act", kind:"click", ref:"e12"}` |
| Type | `browser` `{action:"act", kind:"type", ref:"e12", text:"…"}` |
| Read message text | `browser` `{action:"text", selector:"…", maxChars:4000}` |
| Close when done | `browser` `{action:"stop"}` |

Pass `targetId` from a snapshot into later `act` calls; re-snapshot after any
navigation before reusing refs.

1. Determine which email to check:
   - Primary (Gmail) by default.
   - Secondary (Zoho) if specified or if the verification was sent there.
2. Navigate to the appropriate webmail:
   - Gmail: `mail.google.com`
   - Zoho: `mail.zoho.com`
3. Log in if not already logged in.
   If any login challenge (CAPTCHA, 2FA, phone verification) appears,
   stop and ask the user to resolve it manually.
4. Search for recent verification/confirmation emails (last 10 minutes).
5. Extract the verification code or confirmation link.
6. Report back with the code/link.
7. If no verification email found, wait 30 seconds, retry once, then
   report not found.

## Constraints

- **Never delete emails.**
- **Never send emails.**
- **Never modify account settings.**
- **Never print credentials in chat.**
- Read-only access to inbox.

## If something goes wrong

- Login challenge → stop, ask user to resolve manually, re-run.
- No verification email found → wait 30s, retry once, then stop.
- Rate limit / unusual activity → stop, tell user to retry later.
- Browser automation errors → retry once, then stop and report.
