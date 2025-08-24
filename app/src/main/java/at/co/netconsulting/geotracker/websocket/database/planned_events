-- public.planned_events definition

-- Drop table

-- DROP TABLE public.planned_events;

CREATE TABLE public.planned_events (
	planned_event_id serial4 NOT NULL,
	user_id int4 NOT NULL,
	planned_event_name varchar(255) NOT NULL,
	planned_event_date date NOT NULL,
	planned_event_type varchar(100) NULL,
	planned_event_country varchar(100) NOT NULL,
	planned_event_city varchar(100) NOT NULL,
	planned_latitude numeric(10, 8) NULL,
	planned_longitude numeric(11, 8) NULL,
	is_entered_and_finished bool DEFAULT false NULL,
	website varchar(500) NULL,
	"comment" text NULL,
	reminder_date_time timestamptz NULL,
	is_reminder_active bool DEFAULT false NULL,
	is_recurring bool DEFAULT false NULL,
	recurring_type varchar(20) NULL,
	recurring_interval int4 DEFAULT 1 NULL,
	recurring_end_date date NULL,
	recurring_days_of_week varchar(20) NULL,
	created_at timestamptz DEFAULT now() NULL,
	updated_at timestamptz DEFAULT now() NULL,
	created_by_user_id int4 NOT NULL,
	is_public bool DEFAULT true NULL,
	CONSTRAINT chk_latitude_range CHECK (((planned_latitude >= ('-90'::integer)::numeric) AND (planned_latitude <= (90)::numeric))),
	CONSTRAINT chk_longitude_range CHECK (((planned_longitude >= ('-180'::integer)::numeric) AND (planned_longitude <= (180)::numeric))),
	CONSTRAINT chk_recurring_interval CHECK ((recurring_interval > 0)),
	CONSTRAINT chk_recurring_type CHECK ((((recurring_type)::text = ANY ((ARRAY['daily'::character varying, 'weekly'::character varying, 'monthly'::character varying, 'yearly'::character varying])::text[])) OR (recurring_type IS NULL))),
	CONSTRAINT planned_events_pkey PRIMARY KEY (planned_event_id)
);
CREATE INDEX idx_planned_events_country_city ON public.planned_events USING btree (planned_event_country, planned_event_city);
CREATE INDEX idx_planned_events_created_at ON public.planned_events USING btree (created_at);
CREATE INDEX idx_planned_events_created_by ON public.planned_events USING btree (created_by_user_id);
CREATE INDEX idx_planned_events_date ON public.planned_events USING btree (planned_event_date);
CREATE INDEX idx_planned_events_public ON public.planned_events USING btree (is_public) WHERE (is_public = true);
CREATE INDEX idx_planned_events_type ON public.planned_events USING btree (planned_event_type);
CREATE UNIQUE INDEX idx_planned_events_unique_event ON public.planned_events USING btree (planned_event_name, planned_event_date, planned_event_country, planned_event_city) WHERE (is_public = true);
CREATE INDEX idx_planned_events_user_id ON public.planned_events USING btree (user_id);


-- public.planned_events foreign keys

ALTER TABLE public.planned_events ADD CONSTRAINT fk_planned_events_created_by FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;
ALTER TABLE public.planned_events ADD CONSTRAINT fk_planned_events_user_id FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;