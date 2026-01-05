-- public.tracking_sessions definition

-- Drop table

-- DROP TABLE public.tracking_sessions;

CREATE TABLE public.tracking_sessions (
	session_id varchar(255) NOT NULL,
	user_id int4 NULL,
	event_name varchar(255) NULL,
	sport_type varchar(100) NULL,
	"comment" text NULL,
	clothing varchar(255) NULL,
	start_date_time timestamptz NULL,
	min_distance_meters int4 NULL,
	min_time_seconds int4 NULL,
	voice_announcement_interval int4 NULL,
	created_at timestamptz DEFAULT now() NULL,
	updated_at timestamptz DEFAULT now() NULL,
	CONSTRAINT tracking_sessions_pkey PRIMARY KEY (session_id)
);
CREATE INDEX idx_sessions_user_id ON public.tracking_sessions USING btree (user_id);
CREATE INDEX idx_sessions_user_start_time ON public.tracking_sessions USING btree (user_id, start_date_time DESC);

-- Table Triggers

create trigger trigger_cleanup_after_session_delete after
delete
    on
    public.tracking_sessions for each row execute function cleanup_after_session_delete();


-- public.tracking_sessions foreign keys

ALTER TABLE public.tracking_sessions ADD CONSTRAINT tracking_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;