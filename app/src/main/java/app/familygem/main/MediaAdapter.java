package app.familygem.main;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Person;

import java.util.List;

import app.familygem.DetailActivity;
import app.familygem.Global;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import app.familygem.constant.Choice;
import app.familygem.constant.Extra;
import app.familygem.constant.Image;
import app.familygem.detail.MediaActivity;
import app.familygem.profile.ProfileActivity;
import app.familygem.util.FileUtil;
import app.familygem.util.MediaUtil;
import app.familygem.visitor.FindStack;
import app.familygem.visitor.MediaContainerList;

/**
 * Adapter for a media gallery made with RecyclerView.
 */
public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.MediaViewHolder> {

    private final List<MediaContainerList.MedCont> mediaList;
    private final boolean detail;

    public MediaAdapter(List<MediaContainerList.MedCont> mediaList, boolean detail) {
        this.mediaList = mediaList;
        this.detail = detail;
    }

    @Override
    public MediaViewHolder onCreateViewHolder(ViewGroup parent, int type) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.media_layout, parent, false);
        return new MediaViewHolder(view, detail);
    }

    @Override
    public void onBindViewHolder(final MediaViewHolder holder, int position) {
        holder.setupMedia(position);
    }

    @Override
    public int getItemCount() {
        return mediaList.size();
    }

    class MediaViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        View view;
        boolean detail;
        Media media;
        Object container;
        ImageView imageView;
        TextView textView;
        TextView numberView;

        MediaViewHolder(View view, boolean detail) {
            super(view);
            this.view = view;
            this.detail = detail;
            imageView = view.findViewById(R.id.media_image);
            textView = view.findViewById(R.id.media_caption);
            numberView = view.findViewById(R.id.media_number);
        }

        void setupMedia(int position) {
            media = mediaList.get(position).media;
            container = mediaList.get(position).container;
            if (detail) {
                MediaUtil.INSTANCE.furnishMedia(media, textView, numberView);
                view.setOnClickListener(this);
                view.setTag(R.id.tag_object, media);
                view.setTag(R.id.tag_container, container);
                // Register context menu
                final AppCompatActivity activity = (AppCompatActivity)view.getContext();
                if (activity instanceof ProfileActivity) // profile.MediaFragment
                    ((ProfileActivity)activity).getPageFragment(0).registerForContextMenu(view);
                else if (activity instanceof MainActivity) // main.MediaFragment
                    activity.getSupportFragmentManager().findFragmentById(R.id.main_fragment).registerForContextMenu(view);
                else // DetailActivity
                    activity.registerForContextMenu(view);
            } else {
                RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(RecyclerView.LayoutParams.WRAP_CONTENT, U.dpToPx(110));
                int margin = U.dpToPx(5);
                params.setMargins(margin, margin, margin, margin);
                view.setLayoutParams(params);
                textView.setVisibility(View.GONE);
                numberView.setVisibility(View.GONE);
            }
            FileUtil.INSTANCE.showImage(media, imageView, Image.GALLERY, view.findViewById(R.id.media_progress));
        }

        @Override
        public void onClick(View view) {
            AppCompatActivity activity = (AppCompatActivity)view.getContext();
            // Choosing a media record from main.MediaFragment: returns the ID of the media record
            if (activity.getIntent().getBooleanExtra(Choice.MEDIA, false)) {
                Intent intent = new Intent();
                intent.putExtra(Extra.MEDIA_ID, media.getId());
                activity.setResult(Activity.RESULT_OK, intent);
                activity.finish();
            } else { // Regular opening of MediaActivity
                Intent intent = new Intent(view.getContext(), MediaActivity.class);
                if (media.getId() != null) { // All the media records
                    Memory.setLeader(media);
                } else if ((activity instanceof ProfileActivity && container instanceof Person) // First-level media in profile.MediaFragment
                        || activity instanceof DetailActivity) { // Media in DetailActivity
                    Memory.add(media);
                } else { // Simple media from main.MediaFragment, or sub-level media from profile.MediaFragment
                    new FindStack(Global.gc, media, true);
                    if (activity instanceof MainActivity) // In main.MediaFragment only
                        intent.putExtra(Extra.ALONE, true); // To make MediaActivity display the cabinet
                }
                view.getContext().startActivity(intent);
            }
        }
    }

    /**
     * RecyclerView to make media icons insensitive to clicks.
     * TODO: however prevents scrolling in Detail
     */
    public static class UnclickableRecyclerView extends RecyclerView {
        boolean detail;

        public UnclickableRecyclerView(Context context, boolean detail) {
            super(context);
            this.detail = detail;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);
            return detail; // When false the grid does not intercept the click
        }
    }
}
