export type RootStackParamList = {
  Login: undefined;
  Inbox: { toast?: string } | undefined;
  Task: { id: string; name?: string };
};
