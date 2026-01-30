-- public.discipline_transitions definition

-- Drop table

-- DROP TABLE public.discipline_transitions;

CREATE TABLE public.discipline_transitions (
	transition_id serial4 NOT NULL,
	session_id varchar(255) NULL,
	user_id int4 NULL,
	event_name varchar(255) NULL,
	discipline_name varchar(50) NOT NULL,
	transition_number int4 NOT NULL,
	transition_timestamp int8 NOT NULL,
	received_at timestamptz DEFAULT now() NULL,
	created_at timestamptz DEFAULT now() NULL,
	CONSTRAINT discipline_transitions_pkey PRIMARY KEY (transition_id)
);
CREATE INDEX idx_discipline_transitions_session_id ON public.discipline_transitions USING btree (session_id);
CREATE INDEX idx_discipline_transitions_timestamp ON public.discipline_transitions USING btree (transition_timestamp);
CREATE INDEX idx_discipline_transitions_user_id ON public.discipline_transitions USING btree (user_id);


-- public.discipline_transitions foreign keys

ALTER TABLE public.discipline_transitions ADD CONSTRAINT discipline_transitions_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.tracking_sessions(session_id) ON DELETE CASCADE;
ALTER TABLE public.discipline_transitions ADD CONSTRAINT discipline_transitions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;