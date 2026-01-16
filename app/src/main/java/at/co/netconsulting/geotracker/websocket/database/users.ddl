-- public.users definition

-- Drop table

-- DROP TABLE public.users;

CREATE TABLE public.users (
	user_id serial4 NOT NULL,
	firstname varchar(100) NOT NULL,
	lastname varchar(100) NULL,
	birthdate varchar(20) NULL,
	height numeric(5, 2) NULL,
	weight numeric(5, 2) NULL,
	created_at timestamptz DEFAULT now() NULL,
	updated_at timestamptz DEFAULT now() NULL,
	CONSTRAINT users_pkey PRIMARY KEY (user_id),
	CONSTRAINT users_unique_name_birth UNIQUE (firstname, lastname, birthdate)
);
CREATE INDEX idx_users_name ON public.users USING btree (firstname, lastname);