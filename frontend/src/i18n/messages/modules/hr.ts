/**
 * HR 模組訊息片段(英 / 繁中)。結構由 `hrEn` 定義,`hrZh: typeof hrEn` 強制兩者 key 完全一致。
 * 通用字串(code / name / status / create / cancel …)沿用 common.* / field.*,不在此重複。
 */
export const hrEn = {
  subtitle: 'Employees, departments and positions',
  tabs: {
    dashboard: 'Dashboard',
    employees: 'Employees',
    departments: 'Departments',
    positions: 'Positions',
  },
  dash: {
    headcount: 'Active headcount',
    departments: 'Departments',
    positions: 'Positions',
    avgSalary: 'Average monthly salary',
    payroll: 'Monthly payroll (active)',
    byDepartment: 'Headcount by department',
  },
  employees: {
    new: 'New employee',
    empty: 'No employees yet.',
    created: 'Employee {code} created',
    th: {
      name: 'Name',
      department: 'Department',
      position: 'Position',
      salary: 'Monthly salary',
    },
    form: {
      firstName: 'First name',
      lastName: 'Last name',
      department: 'Department',
      position: 'Position',
      salary: 'Monthly salary',
      hireDate: 'Hire date',
      status: 'Status',
    },
  },
  departments: {
    new: 'New department',
    empty: 'No departments yet.',
    created: 'Department {code} created',
    th: { budget: 'Budget account' },
    form: { budgetAccount: 'Budget GL account' },
  },
  positions: {
    new: 'New position',
    empty: 'No positions yet.',
    created: 'Position {code} created',
    th: { title: 'Title', salary: 'Standard salary' },
    form: { title: 'Title', standardSalary: 'Standard monthly salary' },
  },
  employmentStatus: {
    ACTIVE: 'Active',
    ON_LEAVE: 'On leave',
    TERMINATED: 'Terminated',
  },
};

export const hrZh: typeof hrEn = {
  subtitle: '員工、部門與職位',
  tabs: {
    dashboard: '總覽',
    employees: '員工',
    departments: '部門',
    positions: '職位',
  },
  dash: {
    headcount: '在職人數',
    departments: '部門數',
    positions: '職位數',
    avgSalary: '平均月薪',
    payroll: '當月薪資總額(在職)',
    byDepartment: '各部門人數',
  },
  employees: {
    new: '新增員工',
    empty: '尚無員工。',
    created: '員工 {code} 已建立',
    th: {
      name: '姓名',
      department: '部門',
      position: '職位',
      salary: '月薪',
    },
    form: {
      firstName: '名',
      lastName: '姓',
      department: '部門',
      position: '職位',
      salary: '月薪',
      hireDate: '到職日',
      status: '狀態',
    },
  },
  departments: {
    new: '新增部門',
    empty: '尚無部門。',
    created: '部門 {code} 已建立',
    th: { budget: '預算科目' },
    form: { budgetAccount: '預算 GL 科目' },
  },
  positions: {
    new: '新增職位',
    empty: '尚無職位。',
    created: '職位 {code} 已建立',
    th: { title: '職稱', salary: '標準薪資' },
    form: { title: '職稱', standardSalary: '標準月薪' },
  },
  employmentStatus: {
    ACTIVE: '在職',
    ON_LEAVE: '請假中',
    TERMINATED: '已離職',
  },
};
