CREATE TABLE `card_gear` (
	`card_id` text NOT NULL,
	`gear_id` text NOT NULL,
	PRIMARY KEY(`card_id`, `gear_id`),
	FOREIGN KEY (`card_id`) REFERENCES `cards`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`gear_id`) REFERENCES `gear`(`id`) ON UPDATE no action ON DELETE restrict
);
--> statement-breakpoint
CREATE INDEX `idx_card_gear_gear` ON `card_gear` (`gear_id`);--> statement-breakpoint
CREATE TABLE `card_kinds` (
	`card_id` text NOT NULL,
	`kind` text NOT NULL,
	PRIMARY KEY(`card_id`, `kind`),
	FOREIGN KEY (`card_id`) REFERENCES `cards`(`id`) ON UPDATE no action ON DELETE cascade,
	CONSTRAINT "card_kinds_kind_check" CHECK("card_kinds"."kind" IN ('idea','source','treatment','technique','constraint','arrangement'))
);
--> statement-breakpoint
CREATE INDEX `idx_card_kinds_kind` ON `card_kinds` (`kind`);--> statement-breakpoint
CREATE TABLE `card_tags` (
	`card_id` text NOT NULL,
	`tag` text NOT NULL,
	PRIMARY KEY(`card_id`, `tag`),
	FOREIGN KEY (`card_id`) REFERENCES `cards`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE TABLE `cards` (
	`id` text PRIMARY KEY NOT NULL,
	`text` text NOT NULL,
	`favorite` integer DEFAULT false NOT NULL,
	`status` text DEFAULT 'active' NOT NULL,
	`author` text NOT NULL,
	`draw_count` integer DEFAULT 0 NOT NULL,
	`last_drawn_at` integer,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	CONSTRAINT "cards_status_check" CHECK("cards"."status" IN ('active','archived')),
	CONSTRAINT "cards_author_check" CHECK("cards"."author" IN ('david','claude'))
);
--> statement-breakpoint
CREATE INDEX `idx_cards_status` ON `cards` (`status`);--> statement-breakpoint
CREATE TABLE `draw_cards` (
	`draw_id` integer NOT NULL,
	`slot` integer NOT NULL,
	`slot_kind` text,
	`card_id` text NOT NULL,
	PRIMARY KEY(`draw_id`, `slot`),
	FOREIGN KEY (`draw_id`) REFERENCES `draws`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`card_id`) REFERENCES `cards`(`id`) ON UPDATE no action ON DELETE cascade,
	CONSTRAINT "draw_cards_slot_kind_check" CHECK("draw_cards"."slot_kind" IS NULL OR "draw_cards"."slot_kind" IN ('idea','source','treatment','technique','constraint','arrangement'))
);
--> statement-breakpoint
CREATE INDEX `idx_draw_cards_card` ON `draw_cards` (`card_id`);--> statement-breakpoint
CREATE TABLE `draws` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`uuid` text NOT NULL,
	`drawn_at` integer NOT NULL,
	`mode` text NOT NULL,
	CONSTRAINT "draws_mode_check" CHECK("draws"."mode" IN ('single','spread'))
);
--> statement-breakpoint
CREATE UNIQUE INDEX `draws_uuid_unique` ON `draws` (`uuid`);--> statement-breakpoint
CREATE INDEX `idx_draws_drawn_at` ON `draws` ("drawn_at" DESC);--> statement-breakpoint
CREATE TABLE `gear` (
	`id` text PRIMARY KEY NOT NULL,
	`name` text NOT NULL,
	`category` text NOT NULL,
	`owned` integer DEFAULT true NOT NULL,
	`in_rack` integer DEFAULT false NOT NULL,
	`sort` integer DEFAULT 0 NOT NULL,
	CONSTRAINT "gear_category_check" CHECK("gear"."category" IN ('strings','keys','amps','pedals','mics','small'))
);
--> statement-breakpoint
CREATE TABLE `settings` (
	`key` text PRIMARY KEY NOT NULL,
	`value` text NOT NULL
);
