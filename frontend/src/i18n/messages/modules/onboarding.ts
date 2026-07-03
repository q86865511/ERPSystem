/**
 * Onboarding-tour message fragment (En + Zh). Merged into the core catalogues by the i18n index.
 */
export const onboardingEn = {
  steps: {
    loginWelcome: {
      title: 'Welcome to Manufacturing ERP',
      description: 'This quick tour shows the key features. Restart anytime from the Help menu.',
    },
    reconciliation: {
      title: 'Reconciliation Health Check',
      description: 'Shows whether the books balance. Check subledgers and clearing accounts before posting.',
    },
    navModules: {
      title: 'Navigate Modules',
      description: 'A typical flow is Purchasing → Inventory → Manufacturing → Sales.',
      tryIt: 'Try visiting Purchasing',
    },
    headerToggles: {
      title: 'Language & Theme',
      description: 'Switch English / Traditional Chinese and toggle light/dark mode.',
    },
  },
  stepAnnouncement: 'Step {current} of {total}',
  completionMessage: 'Tour complete — explore at your own pace.',
  restartTour: 'Restart tour',
  nextStep: 'Next',
  previousStep: 'Back',
  skipTour: 'Skip',
};

export const onboardingZh: typeof onboardingEn = {
  steps: {
    loginWelcome: {
      title: '歡迎使用製造業 ERP',
      description: '這個快速導覽會介紹關鍵功能,隨時可從「說明」選單重新開始。',
    },
    reconciliation: {
      title: '對帳健康檢查',
      description: '顯示帳目是否平衡,過帳前請先檢查子帳與過渡科目。',
    },
    navModules: {
      title: '瀏覽各模組',
      description: '典型流程為採購 → 庫存 → 製造 → 銷售。',
      tryIt: '試著前往「採購」',
    },
    headerToggles: {
      title: '語言與主題',
      description: '切換繁體中文/英文,並可切換淺色/深色模式。',
    },
  },
  stepAnnouncement: '第 {current} 步,共 {total} 步',
  completionMessage: '導覽完成 —— 盡情探索吧。',
  restartTour: '重新開始導覽',
  nextStep: '下一步',
  previousStep: '上一步',
  skipTour: '略過',
};
