package app.familygem;

import android.content.Context;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.folg.gedcom.model.Note;
import java.util.Iterator;
import java.util.List;
import app.familygem.visitor.NoteReferences;

public class NotebookAdapter extends RecyclerView.Adapter<NotebookAdapter.ViewHolder> implements Filterable {

	List<Note> noteList;
	private final LayoutInflater inflater;
	private final boolean sharedOnly;
	private ItemClickListener clickListener;
	Note selectedNote;

	NotebookAdapter(Context context, List<Note> data, boolean sharedOnly) {
		this.inflater = LayoutInflater.from(context);
		this.noteList = data;
		this.sharedOnly = sharedOnly;
	}

	@Override
	public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		View view = inflater.inflate(R.layout.quaderno_pezzo, parent, false);
		return new ViewHolder(view);
	}

	@Override
	public void onBindViewHolder(ViewHolder holder, int position) {
		Note note = noteList.get(position);
		if( note.getId() == null )
			holder.countView.setVisibility(View.GONE);
		else {
			holder.countView.setVisibility(View.VISIBLE);
			NoteReferences contaUso = new NoteReferences(Global.gc, note.getId(), false);
			holder.countView.setText(String.valueOf(contaUso.count));
		}
		holder.itemView.setTag(note); // per il menu contestuale Elimina
		holder.textView.setText(note.getValue());
	}

	@Override
	public int getItemCount() {
		return noteList.size();
	}

	@Override
	public Filter getFilter() {
		return new Filter() {
			@Override
			protected FilterResults performFiltering(CharSequence charSequence) {
				String query = charSequence.toString();
				noteList = NotebookFragment.getAllNotes(sharedOnly);
				if( !query.isEmpty() ) {
					Iterator<Note> noteIterator = noteList.iterator();
					while( noteIterator.hasNext() ) {
						Note note = noteIterator.next();
						if( note.getValue() == null || !note.getValue().toLowerCase().contains(query.toLowerCase()) ) {
							noteIterator.remove();
						}
					}
				}
				FilterResults filterResults = new FilterResults();
				filterResults.values = noteList;
				return filterResults;
			}
			@Override
			protected void publishResults(CharSequence cs, FilterResults fr) {
				notifyDataSetChanged();
			}
		};
	}

	public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnCreateContextMenuListener {
		TextView textView;
		TextView countView;

		ViewHolder(View itemView) {
			super(itemView);
			textView = itemView.findViewById(R.id.nota_testo);
			countView = itemView.findViewById(R.id.nota_citazioni);
			itemView.setOnClickListener(this);
			itemView.setOnCreateContextMenuListener(this);
		}

		@Override
		public void onClick(View view) {
			if( clickListener != null ) clickListener.onItemClick(view, getAdapterPosition());
		}

		@Override
		public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
			selectedNote = (Note)v.getTag();
			menu.add(0, 0, 0, R.string.delete);
		}
	}

	Note getItem(int id) {
		return noteList.get(id);
	}

	void setClickListener(ItemClickListener itemClickListener) {
		this.clickListener = itemClickListener;
	}

	public interface ItemClickListener {
		void onItemClick(View view, int position);
	}
}