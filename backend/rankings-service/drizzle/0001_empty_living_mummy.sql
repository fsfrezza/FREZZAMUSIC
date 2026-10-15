CREATE TABLE `listener_logins` (
	`listener` text NOT NULL,
	`user_id` text NOT NULL,
	`linked_at` integer NOT NULL,
	PRIMARY KEY(`listener`, `user_id`),
	FOREIGN KEY (`listener`) REFERENCES `listeners`(`token_hash`) ON UPDATE no action ON DELETE no action,
	FOREIGN KEY (`user_id`) REFERENCES `profiles`(`user_id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE TABLE `profiles` (
	`user_id` text PRIMARY KEY NOT NULL,
	`email` text,
	`email_verified` integer DEFAULT 0 NOT NULL,
	`display_name` text,
	`created_at` integer NOT NULL,
	`last_seen_at` integer NOT NULL
);
--> statement-breakpoint
ALTER TABLE `likes` ADD `user_id` text REFERENCES profiles(user_id);--> statement-breakpoint
ALTER TABLE `plays` ADD `user_id` text REFERENCES profiles(user_id);--> statement-breakpoint
ALTER TABLE `listening_sessions` ADD `user_id` text REFERENCES profiles(user_id);