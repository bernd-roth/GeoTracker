-- public.heart_rate_devices definition

-- Drop table

-- DROP TABLE public.heart_rate_devices;

CREATE TABLE public.heart_rate_devices (
	device_id serial4 NOT NULL,
	device_name varchar(100) NOT NULL,
	created_at timestamptz DEFAULT now() NULL,
	CONSTRAINT heart_rate_devices_device_name_key UNIQUE (device_name),
	CONSTRAINT heart_rate_devices_pkey PRIMARY KEY (device_id)
);