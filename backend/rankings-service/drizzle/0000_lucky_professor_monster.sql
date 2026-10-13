CREATE TABLE `likes` (
	`listener` text NOT NULL,
	`track_id` text NOT NULL,
	`active` integer NOT NULL,
	`sequence` integer NOT NULL,
	PRIMARY KEY(`listener`, `track_id`),
	FOREIGN KEY (`listener`) REFERENCES `listeners`(`token_hash`) ON UPDATE no action ON DELETE no action,
	FOREIGN KEY (`track_id`) REFERENCES `tracks`(`drive_id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE INDEX `likes_track` ON `likes` (`track_id`,`active`);--> statement-breakpoint
CREATE TABLE `rate_limits` (
	`rate_key` text NOT NULL,
	`window` integer NOT NULL,
	`count` integer NOT NULL,
	PRIMARY KEY(`rate_key`, `window`)
);
--> statement-breakpoint
CREATE TABLE `listeners` (
	`token_hash` text PRIMARY KEY NOT NULL,
	`app` text NOT NULL,
	`created_at` integer NOT NULL
);
--> statement-breakpoint
CREATE TABLE `plays` (
	`event_id` text PRIMARY KEY NOT NULL,
	`listener` text NOT NULL,
	`track_id` text NOT NULL,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`event_id`) REFERENCES `listening_sessions`(`event_id`) ON UPDATE no action ON DELETE no action,
	FOREIGN KEY (`listener`) REFERENCES `listeners`(`token_hash`) ON UPDATE no action ON DELETE no action,
	FOREIGN KEY (`track_id`) REFERENCES `tracks`(`drive_id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE INDEX `plays_track` ON `plays` (`track_id`);--> statement-breakpoint
CREATE TABLE `listening_sessions` (
	`event_id` text PRIMARY KEY NOT NULL,
	`listener` text NOT NULL,
	`track_id` text NOT NULL,
	`started_at` integer NOT NULL,
	FOREIGN KEY (`listener`) REFERENCES `listeners`(`token_hash`) ON UPDATE no action ON DELETE no action,
	FOREIGN KEY (`track_id`) REFERENCES `tracks`(`drive_id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE TABLE `tracks` (
	`drive_id` text PRIMARY KEY NOT NULL,
	`folder_id` text NOT NULL,
	`artist` text NOT NULL,
	`title` text NOT NULL,
	`album` text NOT NULL,
	`cover_id` text,
	`verified_at` integer NOT NULL
);
