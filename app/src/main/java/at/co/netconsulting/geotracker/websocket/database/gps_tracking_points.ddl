-- public.gps_tracking_points definition

-- Drop table

-- DROP TABLE public.gps_tracking_points;

CREATE TABLE public.gps_tracking_points (
	id serial4 NOT NULL,
	session_id varchar(255) NULL,
	latitude numeric(10, 8) NOT NULL,
	longitude numeric(11, 8) NOT NULL,
	altitude numeric(10, 4) NULL,
	horizontal_accuracy numeric(8, 4) NULL,
	vertical_accuracy_meters numeric(8, 4) NULL,
	number_of_satellites int4 NULL,
	used_number_of_satellites int4 NULL,
	current_speed numeric(8, 4) NOT NULL,
	average_speed numeric(8, 4) NOT NULL,
	max_speed numeric(8, 4) NOT NULL,
	moving_average_speed numeric(8, 4) NOT NULL,
	speed numeric(8, 4) NULL,
	speed_accuracy_meters_per_second numeric(8, 4) NULL,
	distance numeric(12, 4) NOT NULL,
	covered_distance numeric(12, 4) NULL,
	cumulative_elevation_gain numeric(10, 4) NULL,
	heart_rate int4 NULL,
	heart_rate_device_id int4 NULL,
	lap int4 DEFAULT 0 NULL,
	received_at timestamptz DEFAULT now() NULL,
	created_at timestamptz DEFAULT now() NULL,
	temperature numeric(5, 2) NULL,
	wind_speed numeric(6, 2) NULL,
	wind_direction numeric(5, 1) NULL,
	humidity int4 NULL,
	weather_timestamp int8 NULL,
	weather_code int4 NULL,
	pressure numeric(8, 2) NULL,
	pressure_accuracy int4 NULL,
	altitude_from_pressure numeric(10, 4) NULL,
	sea_level_pressure numeric(8, 2) NULL,
	slope numeric(6, 2) NULL,
	average_slope numeric(6, 2) NULL,
	max_uphill_slope numeric(6, 2) NULL,
	max_downhill_slope numeric(6, 2) NULL,
	CONSTRAINT gps_tracking_points_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_gps_altitude_from_pressure ON public.gps_tracking_points USING btree (altitude_from_pressure) WHERE (altitude_from_pressure IS NOT NULL);
CREATE INDEX idx_gps_location ON public.gps_tracking_points USING btree (latitude, longitude);
CREATE INDEX idx_gps_pressure ON public.gps_tracking_points USING btree (pressure) WHERE (pressure IS NOT NULL);
CREATE INDEX idx_gps_received_at ON public.gps_tracking_points USING btree (received_at);
CREATE INDEX idx_gps_session_id ON public.gps_tracking_points USING btree (session_id);
CREATE INDEX idx_gps_temperature ON public.gps_tracking_points USING btree (temperature) WHERE (temperature IS NOT NULL);
CREATE INDEX idx_gps_weather_code ON public.gps_tracking_points USING btree (weather_code) WHERE (weather_code IS NOT NULL);
CREATE INDEX idx_gps_wind_speed ON public.gps_tracking_points USING btree (wind_speed) WHERE (wind_speed IS NOT NULL);


-- public.gps_tracking_points foreign keys

ALTER TABLE public.gps_tracking_points ADD CONSTRAINT gps_tracking_points_heart_rate_device_id_fkey FOREIGN KEY (heart_rate_device_id) REFERENCES public.heart_rate_devices(device_id);
ALTER TABLE public.gps_tracking_points ADD CONSTRAINT gps_tracking_points_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.tracking_sessions(session_id) ON DELETE CASCADE;