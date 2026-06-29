/** Audit-trail viewer strings (ADMIN-only page). Merged into the core catalogues under `audit.*`. */
export const auditEn = {
  title: 'Audit Trail',
  subtitle: 'Sensitive actions — postings, period changes and logins (newest first).',
  filter: {
    event: 'Event type',
    all: 'All events',
    actor: 'Actor',
    actorPlaceholder: 'Filter by actor',
  },
  col: {
    time: 'Time',
    event: 'Event',
    actor: 'Actor',
    summary: 'Summary',
    ref: 'Reference',
  },
  event: {
    JOURNAL_POSTED: 'Journal posted',
    PERIOD_CLOSED: 'Period closed',
    PERIOD_REOPENED: 'Period reopened',
    LOGIN_SUCCESS: 'Login success',
    LOGIN_FAILURE: 'Login failure',
  },
  empty: 'No audit records.',
  total: '{count} records',
};

export const auditZh: typeof auditEn = {
  title: '審計軌跡',
  subtitle: '敏感動作 —— 過帳、期間異動與登入(最新在前)。',
  filter: {
    event: '事件類型',
    all: '全部事件',
    actor: '操作者',
    actorPlaceholder: '依操作者篩選',
  },
  col: {
    time: '時間',
    event: '事件',
    actor: '操作者',
    summary: '摘要',
    ref: '參照',
  },
  event: {
    JOURNAL_POSTED: '過帳',
    PERIOD_CLOSED: '期間關閉',
    PERIOD_REOPENED: '期間重開',
    LOGIN_SUCCESS: '登入成功',
    LOGIN_FAILURE: '登入失敗',
  },
  empty: '無審計紀錄。',
  total: '{count} 筆',
};
