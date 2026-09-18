import { beforeEach, describe, expect, it } from 'vitest';
import {
	SESSION_MAX_AGE,
	constantTimeEqual,
	createSessionToken,
	isRateLimited,
	recordFailedAttempt,
	clearAttempts,
	resetRateLimits,
	verifySessionToken
} from './auth';

const SECRET = 'a'.repeat(64);

describe('session tokens', () => {
	it('verifies a token it just minted', () => {
		expect(verifySessionToken(createSessionToken(SECRET), SECRET)).toBe(true);
	});

	it('rejects a token signed with a different secret', () => {
		const token = createSessionToken(SECRET);
		expect(verifySessionToken(token, 'b'.repeat(64))).toBe(false);
	});

	it('rejects a tampered payload', () => {
		const now = Date.now();
		const token = createSessionToken(SECRET, now);
		const [, signature] = token.split('.');
		// Re-date the session without re-signing it: the signature no longer matches.
		const forged = Buffer.from(JSON.stringify({ iat: now + 1000 })).toString('base64url');
		expect(verifySessionToken(`${forged}.${signature}`, SECRET)).toBe(false);
	});

	it('rejects a malformed token', () => {
		expect(verifySessionToken('', SECRET)).toBe(false);
		expect(verifySessionToken('nodot', SECRET)).toBe(false);
		expect(verifySessionToken('.sig', SECRET)).toBe(false);
	});

	it('expires after one year', () => {
		const now = Date.now();
		const token = createSessionToken(SECRET, now);
		expect(verifySessionToken(token, SECRET, now + SESSION_MAX_AGE * 1000 - 1)).toBe(true);
		expect(verifySessionToken(token, SECRET, now + SESSION_MAX_AGE * 1000 + 1)).toBe(false);
	});
});

describe('constantTimeEqual', () => {
	it('matches equal strings and rejects others, including at different lengths', () => {
		expect(constantTimeEqual('hunter2', 'hunter2')).toBe(true);
		expect(constantTimeEqual('hunter2', 'hunter3')).toBe(false);
		expect(constantTimeEqual('hunter2', 'much longer than that')).toBe(false);
		expect(constantTimeEqual('', '')).toBe(true);
	});
});

describe('rate limiting', () => {
	beforeEach(resetRateLimits);

	it('allows ten failures then blocks the eleventh', () => {
		for (let i = 0; i < 10; i++) {
			expect(isRateLimited('1.2.3.4')).toBe(false);
			recordFailedAttempt('1.2.3.4');
		}
		expect(isRateLimited('1.2.3.4')).toBe(true);
	});

	it('tracks each IP separately', () => {
		for (let i = 0; i < 10; i++) recordFailedAttempt('1.2.3.4');
		expect(isRateLimited('1.2.3.4')).toBe(true);
		expect(isRateLimited('5.6.7.8')).toBe(false);
	});

	it('forgets attempts older than fifteen minutes', () => {
		const now = Date.now();
		for (let i = 0; i < 10; i++) recordFailedAttempt('1.2.3.4', now);
		expect(isRateLimited('1.2.3.4', now)).toBe(true);
		expect(isRateLimited('1.2.3.4', now + 15 * 60 * 1000 + 1)).toBe(false);
	});

	it('clears an IP after a successful login', () => {
		for (let i = 0; i < 10; i++) recordFailedAttempt('1.2.3.4');
		clearAttempts('1.2.3.4');
		expect(isRateLimited('1.2.3.4')).toBe(false);
	});
});
