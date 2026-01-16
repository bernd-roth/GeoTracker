-- public.lap_times definition

-- Drop table

-- DROP TABLE public.lap_times;

CREATE TABLE public.lap_times (
	id serial4 NOT NULL,
	session_id varchar(255) NOT NULL,
	user_id int4 NULL,
	lap_number int4 NOT NULL,
	start_time int8 NOT NULL,
	end_time int8 NOT NULL,
	duration int8 GENERATED ALWAYS AS (end_time - start_time) STORED NULL,
	distance numeric(8, 4) DEFAULT 1.0 NOT NULL,
	created_at timestamptz DEFAULT now() NULL,
	CONSTRAINT lap_times_pkey PRIMARY KEY (id),
	CONSTRAINT lap_times_session_id_lap_number_key UNIQUE (session_id, lap_number)
);


-- public.lap_times foreign keys

ALTER TABLE public.lap_times ADD CONSTRAINT lap_times_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);