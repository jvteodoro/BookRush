export async function updateSavedBook(
  previous: boolean,
  render: (saved: boolean) => void,
  persist: (saved: boolean) => Promise<void>,
) {
  render(!previous);
  try {
    await persist(!previous);
  } catch (cause) {
    render(previous);
    throw cause;
  }
}
