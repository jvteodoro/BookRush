// Host-element stand-ins for the react-native components used by the family screen.
import { createElement } from "react";

const host = (name) => {
  const Host = (props) => createElement(name, props, props.children);
  Host.displayName = name;
  return Host;
};

export const View = host("View");
export const Text = host("Text");
export const TextInput = host("TextInput");
export const ScrollView = host("ScrollView");
export const SafeAreaView = host("SafeAreaView");
export const Pressable = host("Pressable");
export const ActivityIndicator = host("ActivityIndicator");
export const FlatList = host("FlatList");
export const StyleSheet = { create: (styles) => styles };
export const Alert = { alert: () => {} };
