--liquibase formatted sql

--changeset ecl:017-add-dict-tables
CREATE TABLE tbl_dict_category (
  category_id   VARCHAR(32)   NOT NULL PRIMARY KEY COMMENT '分类ID（UUID）',
  category_code VARCHAR(64)   NOT NULL COMMENT '分类编码，如 CUSTOMER_TYPE / COLLATERAL_TYPE / INDUSTRY / LOAN_CLASSIFICATION',
  category_name VARCHAR(128)  NOT NULL COMMENT '分类名称，如"客户类型"/"担保类型"',
  description   VARCHAR(256)  NULL     COMMENT '说明',
  is_system     TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否系统预置（1=是，不允许删除）',
  sort_order    INT           NOT NULL DEFAULT 0  COMMENT '排序',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      NULL     ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_category_code (category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典分类';

--changeset ecl:017-add-dict-entries
CREATE TABLE tbl_dict_entry (
  entry_id      VARCHAR(32)   NOT NULL PRIMARY KEY COMMENT '条目ID（UUID）',
  category_id   VARCHAR(32)   NOT NULL COMMENT '所属分类',
  entry_code    VARCHAR(64)   NOT NULL COMMENT '条目编码，如 MORTGAGE / PLEDGE',
  entry_name    VARCHAR(128)  NOT NULL COMMENT '条目名称，如"抵押"/"质押"',
  sort_order    INT           NOT NULL DEFAULT 0  COMMENT '排序',
  is_active     TINYINT(1)    NOT NULL DEFAULT 1  COMMENT '是否启用',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      NULL     ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_category_entry (category_id, entry_code),
  CONSTRAINT fk_entry_category FOREIGN KEY (category_id) REFERENCES tbl_dict_category(category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典条目';

--changeset ecl:017-add-scheme-dict
CREATE TABLE tbl_scheme_dict (
  id            VARCHAR(32)   NOT NULL PRIMARY KEY COMMENT 'ID（UUID）',
  scheme_id     VARCHAR(32)   NOT NULL COMMENT '方案ID',
  category_id   VARCHAR(32)   NOT NULL COMMENT '字典分类ID',
  override_type VARCHAR(16)   NOT NULL DEFAULT 'INHERIT' COMMENT '覆盖策略：INHERIT=继承全局，CUSTOM=自定义',
  entry_ids     TEXT          NULL     COMMENT 'CUSTOM 模式下选中的条目ID（JSON数组）',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      NULL     ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_scheme_category (scheme_id, category_id),
  CONSTRAINT fk_scheme_dict_scheme   FOREIGN KEY (scheme_id)   REFERENCES tbl_ecl_scheme(scheme_id),
  CONSTRAINT fk_scheme_dict_category FOREIGN KEY (category_id) REFERENCES tbl_dict_category(category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='方案基础信息（字典覆盖）';

--changeset ecl:017-seed-categories
INSERT IGNORE INTO tbl_dict_category (category_id, category_code, category_name, description, is_system, sort_order) VALUES
('dict_cat_customer_type', 'CUSTOMER_TYPE',   '客户类型',   'ECL 减值计算的客户分类', 1, 1),
('dict_cat_collateral',    'COLLATERAL_TYPE',  '担保类型',   '抵质押担保方式分类',    1, 2),
('dict_cat_industry',      'INDUSTRY',         '行业分类',   '国民经济行业分类（门类）', 1, 3),
('dict_cat_loan_class',    'LOAN_CLASSIFICATION', '五级分类', '贷款五级分类',          1, 4),
('dict_cat_product_type',  'PRODUCT_TYPE',     '产品类型',   '授信产品类型',          1, 5);

--changeset ecl:017-seed-entries
INSERT IGNORE INTO tbl_dict_entry (entry_id, category_id, entry_code, entry_name, sort_order) VALUES
('de_ct_01', 'dict_cat_customer_type', 'CORPORATE',     '对公',     1),
('de_ct_02', 'dict_cat_customer_type', 'RETAIL',       '零售',     2),
('de_ct_03', 'dict_cat_customer_type', 'FINANCIAL',    '金融机构',  3),
('de_ct_04', 'dict_cat_customer_type', 'INTERBANK',    '同业',     4),
('de_ct_05', 'dict_cat_customer_type', 'GOVERNMENT',   '政府',     5);

INSERT INTO tbl_dict_entry (entry_id, category_id, entry_code, entry_name, sort_order) VALUES
('de_col_01', 'dict_cat_collateral', 'MORTGAGE',   '抵押',  1),
('de_col_02', 'dict_cat_collateral', 'PLEDGE',     '质押',  2),
('de_col_03', 'dict_cat_collateral', 'CREDIT',     '信用',  3),
('de_col_04', 'dict_cat_collateral', 'GUARANTEE',  '保证',  4),
('de_col_05', 'dict_cat_collateral', 'DEPOSIT',    '保证金', 5);

INSERT INTO tbl_dict_entry (entry_id, category_id, entry_code, entry_name, sort_order) VALUES
('de_ind_A',  'dict_cat_industry', 'A', '农、林、牧、渔业',       1),
('de_ind_B',  'dict_cat_industry', 'B', '采矿业',               2),
('de_ind_C',  'dict_cat_industry', 'C', '制造业',               3),
('de_ind_D',  'dict_cat_industry', 'D', '电力、热力、燃气及水',   4),
('de_ind_E',  'dict_cat_industry', 'E', '建筑业',               5),
('de_ind_F',  'dict_cat_industry', 'F', '批发和零售业',          6),
('de_ind_G',  'dict_cat_industry', 'G', '交通运输、仓储和邮政业', 7),
('de_ind_H',  'dict_cat_industry', 'H', '住宿和餐饮业',          8),
('de_ind_I',  'dict_cat_industry', 'I', '信息传输、软件和信息技术',9),
('de_ind_J',  'dict_cat_industry', 'J', '金融业',               10),
('de_ind_K',  'dict_cat_industry', 'K', '房地产业',             11),
('de_ind_L',  'dict_cat_industry', 'L', '租赁和商务服务业',      12),
('de_ind_M',  'dict_cat_industry', 'M', '科学研究和技术服务业',   13),
('de_ind_N',  'dict_cat_industry', 'N', '水利、环境和公共设施',   14),
('de_ind_O',  'dict_cat_industry', 'O', '居民服务、修理和其他',   15),
('de_ind_P',  'dict_cat_industry', 'P', '教育',                 16),
('de_ind_Q',  'dict_cat_industry', 'Q', '卫生和社会工作',        17),
('de_ind_R',  'dict_cat_industry', 'R', '文化、体育和娱乐业',    18),
('de_ind_S',  'dict_cat_industry', 'S', '公共管理、社会保障',     19);

INSERT INTO tbl_dict_entry (entry_id, category_id, entry_code, entry_name, sort_order) VALUES
('de_lc_01', 'dict_cat_loan_class', 'NORMAL',    '正常', 1),
('de_lc_02', 'dict_cat_loan_class', 'ATTENTION', '关注', 2),
('de_lc_03', 'dict_cat_loan_class', 'SUBPRIME',  '次级', 3),
('de_lc_04', 'dict_cat_loan_class', 'DOUBTFUL',  '可疑', 4),
('de_lc_05', 'dict_cat_loan_class', 'LOSS',      '损失', 5);

INSERT INTO tbl_dict_entry (entry_id, category_id, entry_code, entry_name, sort_order) VALUES
('de_pt_01', 'dict_cat_product_type', 'WORKING_CAPITAL', '流动资金贷款',  1),
('de_pt_02', 'dict_cat_product_type', 'FIXED_ASSET',    '固定资产贷款',  2),
('de_pt_03', 'dict_cat_product_type', 'BILL',           '票据业务',      3),
('de_pt_04', 'dict_cat_product_type', 'LC',             '信用证',        4),
('de_pt_05', 'dict_cat_product_type', 'GUARANTEE_BIZ',  '保函',          5),
('de_pt_06', 'dict_cat_product_type', 'FACTORING',      '保理',          6);

--changeset ecl:017-seed-commitment-type context:seed
INSERT IGNORE INTO tbl_dict_category (category_id, category_code, category_name, description, is_system, sort_order) VALUES
('dict_cat_commitment', 'COMMITMENT_TYPE', '承诺类型', '授信承诺类型（CCF适用）', 1, 6);

INSERT IGNORE INTO tbl_dict_entry (entry_id, category_id, entry_code, entry_name, sort_order) VALUES
('de_cmt_01', 'dict_cat_commitment', 'REVOLVING',     '循环授信',     1),
('de_cmt_02', 'dict_cat_commitment', 'TERM_LOAN',     '定期贷款',     2),
('de_cmt_03', 'dict_cat_commitment', 'STANDBY_LC',    '备用信用证',   3),
('de_cmt_04', 'dict_cat_commitment', 'BILL_ACCEPT',   '票据承兑',     4);

--changeset ecl:017-seed-collateral-category context:seed
INSERT IGNORE INTO tbl_dict_category (category_id, category_code, category_name, description, is_system, sort_order) VALUES
('dict_cat_collateral_category', 'COLLATERAL_CATEGORY', '押品大类', '抵质押品大类分类（LGD折扣率适用）', 1, 7);

INSERT IGNORE INTO tbl_dict_entry (entry_id, category_id, entry_code, entry_name, sort_order) VALUES
('de_cc_01', 'dict_cat_collateral_category', 'REAL_ESTATE',  '房地产',   1),
('de_cc_02', 'dict_cat_collateral_category', 'EQUIPMENT',   '设备',     2),
('de_cc_03', 'dict_cat_collateral_category', 'FINANCIAL',   '金融资产', 3),
('de_cc_04', 'dict_cat_collateral_category', 'INVENTORY',   '存货',     4),
('de_cc_05', 'dict_cat_collateral_category', 'RECEIVABLE',  '应收账款', 5),
('de_cc_06', 'dict_cat_collateral_category', 'GUARANTEE',   '保证',     6),
('de_cc_07', 'dict_cat_collateral_category', 'DEPOSIT',     '保证金',   7),
('de_cc_08', 'dict_cat_collateral_category', 'OTHER',       '其他',     8);
