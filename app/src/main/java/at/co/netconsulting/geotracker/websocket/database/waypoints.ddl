-- public.waypoints definition

-- Drop table

-- DROP TABLE public.waypoints;

CREATE TABLE public.waypoints (
	waypoint_id serial4 NOT NULL,
	session_id varchar(255) NULL,
	user_id int4 NULL,
	event_name varchar(255) NULL,
	waypoint_name varchar(255) NOT NULL,
	waypoint_description text NULL,
	latitude numeric(10, 8) NOT NULL,
	longitude numeric(11, 8) NOT NULL,
	elevation numeric(10, 4) NULL,
	waypoint_timestamp int8 NOT NULL,
	received_at timestamptz DEFAULT now() NULL,
	created_at timestamptz DEFAULT now() NULL,
	CONSTRAINT waypoints_pkey PRIMARY KEY (waypoint_id)
);
CREATE INDEX idx_waypoints_location ON public.waypoints USING btree (latitude, longitude);
CREATE INDEX idx_waypoints_received_at ON public.waypoints USING btree (received_at);
CREATE INDEX idx_waypoints_session_id ON public.waypoints USING btree (session_id);
CREATE INDEX idx_waypoints_timestamp ON public.waypoints USING btree (waypoint_timestamp);
CREATE INDEX idx_waypoints_user_id ON public.waypoints USING btree (user_id);


-- public.waypoints foreign keys

ALTER TABLE public.waypoints ADD CONSTRAINT waypoints_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.tracking_sessions(session_id) ON DELETE CASCADE;
ALTER TABLE public.waypoints ADD CONSTRAINT waypoints_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;