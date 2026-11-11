-- Margaretha Editor account backend.
-- Authentication itself is Supabase Auth; the display name lives in the user's own metadata.
-- Projects and media stay on the device, so there is no project table to protect.

-- Lets a signed-in user delete their own account. SECURITY DEFINER is required because
-- ordinary users cannot touch auth.users; the function can only ever act on auth.uid().
create or replace function public.delete_own_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if (select auth.uid()) is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  delete from auth.users where id = (select auth.uid());
end;
$$;

revoke all on function public.delete_own_account() from public, anon;
grant execute on function public.delete_own_account() to authenticated;
