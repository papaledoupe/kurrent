CREATE TABLE `events` (
  `aggregate_type` VARCHAR(32) NOT NULL,
  `aggregate_id` VARCHAR(64) NOT NULL,
  `aggregate_version` INT UNSIGNED NOT NULL,
  `event` VARCHAR(64) NOT NULL,
  `data` BLOB NOT NULL,
  `timestamp` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`aggregate_type`, `aggregate_id`, `aggregate_version`)
) ENGINE=InnoDB CHARSET=UTF8MB4;