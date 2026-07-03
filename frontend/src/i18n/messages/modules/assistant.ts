/** AI assistant side-panel strings. Merged into the core catalogues under `assistant.*`. */
export const assistantEn = {
  title: 'ERP Copilot',
  open: 'Open assistant',
  close: 'Close assistant',
  clear: 'Clear conversation',
  empty: 'Ask me anything about your ERP data.',
  inputPlaceholder: 'Message the assistant…',
  send: 'Send',
  stop: 'Stop',
  tool: {
    running: 'Running',
    confirm: 'Confirm',
    reject: 'Reject',
    toggleResult: 'Toggle result',
    rejectedResult: 'Rejected by user.',
    cancelledResult: 'Cancelled by user.',
    status: {
      proposed: 'Awaiting approval',
      running: 'Running',
      success: 'Done',
      error: 'Failed',
    },
  },
  error: {
    title: 'Something went wrong',
    generic: 'The assistant could not complete the request.',
    network: 'Connection lost. Please try again.',
    rateLimited: 'Too many requests',
    rateLimitedDetail: 'You have sent too many messages. Please wait a moment and try again.',
  },
};

export const assistantZh: typeof assistantEn = {
  title: 'ERP 智慧助手',
  open: '開啟助手',
  close: '關閉助手',
  clear: '清空對話',
  empty: '有任何關於 ERP 資料的問題都可以問我。',
  inputPlaceholder: '對助手輸入訊息…',
  send: '送出',
  stop: '停止',
  tool: {
    running: '執行中',
    confirm: '確認',
    reject: '拒絕',
    toggleResult: '展開/收合結果',
    rejectedResult: '使用者已拒絕。',
    cancelledResult: '使用者已取消。',
    status: {
      proposed: '待核准',
      running: '執行中',
      success: '完成',
      error: '失敗',
    },
  },
  error: {
    title: '發生錯誤',
    generic: '助手無法完成這個請求。',
    network: '連線中斷,請再試一次。',
    rateLimited: '請求過於頻繁',
    rateLimitedDetail: '你送出的訊息太多了,請稍候片刻再試。',
  },
};
