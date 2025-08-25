-- public.tracking_data definition

-- Drop table

-- DROP TABLE public.tracking_data;

CREATE TABLE public.tracking_data (
	id bigserial NOT NULL,
	session_id varchar(255) NOT NULL,
	person varchar(100) NULL,
	latitude numeric(10, 8) NOT NULL,
	longitude numeric(11, 8) NOT NULL,
	altitude numeric(10, 4) NULL,
	horizontal_accuracy numeric(8, 4) NULL,
	vertical_accuracy_meters numeric(8, 4) NULL,
	number_of_satellites int4 NULL,
	satellites int4 NULL,
	used_number_of_satellites int4 NULL,
	current_speed numeric(8, 4) NULL,
	average_speed numeric(8, 4) NULL,
	max_speed numeric(8, 4) NULL,
	moving_average_speed numeric(8, 4) NULL,
	speed numeric(8, 4) NULL,
	speed_accuracy_meters_per_second numeric(8, 4) NULL,
	distance numeric(12, 4) NULL,
	covered_distance numeric(12, 4) NULL,
	cumulative_elevation_gain numeric(10, 4) NULL,
	heart_rate int4 NULL,
	heart_rate_device varchar(100) NULL,
	lap int4 NULL,
	start_date_time timestamptz NULL,
	received_at timestamptz DEFAULT CURRENT_TIMESTAMP NULL,
	created_at timestamptz DEFAULT CURRENT_TIMESTAMP NULL,
	firstname varchar(100) NULL,
	lastname varchar(100) NULL,
	birthdate varchar(20) NULL,
	height numeric(5, 2) NULL,
	weight numeric(5, 2) NULL,
	min_distance_meters int4 NULL,
	min_time_seconds int4 NULL,
	voice_announcement_interval int4 NULL,
	event_name varchar(255) NULL,
	sport_type varchar(100) NULL,
	"comment" text NULL,
	clothing varchar(255) NULL,
	CONSTRAINT tracking_data_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_tracking_data_event_name ON public.tracking_data USING btree (event_name);
CREATE INDEX idx_tracking_data_firstname ON public.tracking_data USING btree (firstname);
CREATE INDEX idx_tracking_data_lastname ON public.tracking_data USING btree (lastname);
CREATE INDEX idx_tracking_data_location ON public.tracking_data USING btree (latitude, longitude);
CREATE INDEX idx_tracking_data_person ON public.tracking_data USING btree (person);
CREATE INDEX idx_tracking_data_received_at ON public.tracking_data USING btree (received_at);
CREATE INDEX idx_tracking_data_session_event ON public.tracking_data USING btree (session_id, event_name, sport_type);
CREATE INDEX idx_tracking_data_session_id ON public.tracking_data USING btree (session_id);
CREATE INDEX idx_tracking_data_settings ON public.tracking_data USING btree (min_distance_meters, min_time_seconds, voice_announcement_interval);
CREATE INDEX idx_tracking_data_sport_type ON public.tracking_data USING btree (sport_type);
CREATE INDEX idx_tracking_data_start_date_time ON public.tracking_data USING btree (start_date_time);