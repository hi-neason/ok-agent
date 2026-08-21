CREATE TABLE solution (
  id BINARY(16) NOT NULL,
  solution_key VARCHAR(128) NOT NULL,
  name VARCHAR(255) NOT NULL,
  description MEDIUMTEXT NULL,
  target_customer VARCHAR(512) NOT NULL DEFAULT '',
  scenario VARCHAR(512) NOT NULL DEFAULT '',
  price_note VARCHAR(512) NOT NULL DEFAULT '',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  updated_by VARCHAR(64) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_solution_key (solution_key),
  KEY idx_solution_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE solution_item (
  id BINARY(16) NOT NULL,
  solution_id BINARY(16) NOT NULL,
  product_id BINARY(16) NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  role VARCHAR(32) NOT NULL DEFAULT 'PRIMARY',
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_solution_item (solution_id, product_id),
  KEY idx_solution_item_product (product_id),
  CONSTRAINT fk_si_solution FOREIGN KEY (solution_id) REFERENCES solution(id) ON DELETE CASCADE,
  CONSTRAINT fk_si_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
