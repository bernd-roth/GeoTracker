-- public.session_media definition

-- Drop table

-- DROP TABLE public.session_media;

CREATE TABLE public.session_media (
	media_id serial4 NOT NULL,
	session_id varchar(255) NOT NULL,
	media_uuid varchar(36) NOT NULL,
	media_type varchar(10) NOT NULL,
	file_extension varchar(10) NOT NULL,
	original_filename varchar(255) NULL,
	file_size_bytes int8 NULL,
	thumbnail_generated bool DEFAULT false NULL,
	caption text NULL,
	sort_order int4 DEFAULT 0 NULL,
	created_at timestamptz DEFAULT CURRENT_TIMESTAMP NULL,
	CONSTRAINT session_media_media_uuid_key UNIQUE (media_uuid),
	CONSTRAINT session_media_pkey PRIMARY KEY (media_id)
);
CREATE INDEX idx_session_media_session_id ON public.session_media USING btree (session_id);


-- public.session_media foreign keys

ALTER TABLE public.session_media ADD CONSTRAINT session_media_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.tracking_sessions(session_id) ON DELETE CASCADE;